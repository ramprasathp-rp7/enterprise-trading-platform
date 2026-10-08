package com.tradingplatform.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.OrderPlacedPayload;
import com.tradingplatform.executor.OrderExecutorService;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.quote.PricingUnavailableException;

@Component
public class OrderPlacedListener {
	private static final Logger logger = LoggerFactory.getLogger(OrderPlacedListener.class);
	private static final int MAX_ATTEMPTS = 3;
	private static final long INITIAL_BACKOFF_MILLIS = 100L;
	private static final double BACKOFF_MULTIPLIER = 2.0d;
	static final long DEFAULT_DEAD_LETTER_SEND_TIMEOUT_MS = 5000L;
	static final long DEFAULT_NACK_BACKOFF_MS = 5000L;

	private final OrderExecutorService executorService;
	private final InstrumentMapper instrumentMapper;
	private final OrderMapper orderMapper;
	/** Sends a dead-letter record; the future completes when Kafka acknowledges or rejects it. */
	private final Function<ProducerRecord<String, String>, CompletableFuture<?>> deadLetterSender;
	private final ObjectMapper objectMapper;
	private final long deadLetterSendTimeoutMs;
	private final long nackBackoffMs;

	@Autowired
	public OrderPlacedListener(
		OrderExecutorService executorService,
		InstrumentMapper instrumentMapper,
		OrderMapper orderMapper,
		KafkaTemplate<String, String> deadLetterKafkaTemplate,
		ObjectMapper objectMapper,
		@Value("${dead-letter.send-timeout-ms:5000}") long deadLetterSendTimeoutMs,
		@Value("${dead-letter.nack-backoff-ms:5000}") long nackBackoffMs
	) {
		this(executorService, instrumentMapper, orderMapper, deadLetterKafkaTemplate::send, objectMapper,
			deadLetterSendTimeoutMs, nackBackoffMs);
	}

	public OrderPlacedListener(
		OrderExecutorService executorService,
		InstrumentMapper instrumentMapper,
		OrderMapper orderMapper,
		Function<ProducerRecord<String, String>, CompletableFuture<?>> deadLetterSender,
		ObjectMapper objectMapper
	) {
		this(executorService, instrumentMapper, orderMapper, deadLetterSender, objectMapper,
			DEFAULT_DEAD_LETTER_SEND_TIMEOUT_MS, DEFAULT_NACK_BACKOFF_MS);
	}

	public OrderPlacedListener(
		OrderExecutorService executorService,
		InstrumentMapper instrumentMapper,
		OrderMapper orderMapper,
		Function<ProducerRecord<String, String>, CompletableFuture<?>> deadLetterSender,
		ObjectMapper objectMapper,
		long deadLetterSendTimeoutMs,
		long nackBackoffMs
	) {
		this.executorService = executorService;
		this.instrumentMapper = instrumentMapper;
		this.orderMapper = orderMapper;
		this.deadLetterSender = deadLetterSender;
		this.objectMapper = objectMapper;
		this.deadLetterSendTimeoutMs = deadLetterSendTimeoutMs;
		this.nackBackoffMs = nackBackoffMs;
	}

	@KafkaListener(topics = "orders", groupId = "trade-executor")
	public void onOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack) {
		MessageEnvelope<OrderPlacedPayload> envelope;
		try {
			envelope = objectMapper.readValue(record.value(), new TypeReference<MessageEnvelope<OrderPlacedPayload>>() {});
		} catch (JsonProcessingException ex) {
			logger.warn("Dead-lettering malformed order message: topic={}, partition={}, offset={}, reason={}",
				record.topic(), record.partition(), record.offset(), ex.getMessage());
			deadLetterAndAck(record, "Malformed JSON: " + ex.getMessage(), ack);
			return;
		}

		if (envelope.eventType() != EventType.ORDER_PLACED) {
			logger.warn("Dead-lettering unexpected event type: topic={}, partition={}, offset={}, eventType={}",
				record.topic(), record.partition(), record.offset(), envelope.eventType());
			deadLetterAndAck(record, "Unexpected event type: " + envelope.eventType(), ack);
			return;
		}

		OrderPlacedPayload payload = envelope.payload();
		if (payload == null
			|| payload.orderId() == null || payload.orderId().isBlank()
			|| payload.accountId() == null
			|| payload.symbol() == null || payload.symbol().isBlank()
			|| payload.side() == null || payload.side().isBlank()
			|| payload.quantity() == null
			|| payload.price() == null
			|| payload.idempotencyKey() == null || payload.idempotencyKey().isBlank()) {
			logger.warn("Dead-lettering order with missing required field: topic={}, partition={}, offset={}, orderId={}",
				record.topic(), record.partition(), record.offset(), payload == null ? null : payload.orderId());
			deadLetterAndAck(record, "Missing required order field", ack);
			return;
		}

		UUID orderUuid;
		try {
			orderUuid = UUID.fromString(payload.orderId());
		} catch (IllegalArgumentException ex) {
			logger.warn("Dead-lettering order with invalid order identifier: topic={}, partition={}, offset={}, orderId={}",
				record.topic(), record.partition(), record.offset(), payload.orderId());
			deadLetterAndAck(record, "Invalid order identifier: " + payload.orderId(), ack);
			return;
		}

		logger.info("Consuming order event: topic={}, partition={}, offset={}, orderId={}, accountId={}",
			record.topic(), record.partition(), record.offset(), payload.orderId(), payload.accountId());

		int attempts = 0;
		long backoffMillis = INITIAL_BACKOFF_MILLIS;
		while (true) {
			try {
				processOnce(payload, orderUuid);
				logger.info("Finished processing order event: orderId={}, accountId={}", payload.orderId(), payload.accountId());
				if (ack != null) {
					ack.acknowledge();
				}
				return;
			} catch (NoSuchElementException | IllegalArgumentException ex) {
				logger.warn("Dead-lettering order after terminal failure: orderId={}, reason={}", payload.orderId(), ex.getMessage());
				deadLetterAndAck(record, ex.getMessage(), ack);
				return;
			} catch (PricingUnavailableException ex) {
				// Pricing is a business outcome, not a transient system failure
				attempts++;
				if (attempts >= MAX_ATTEMPTS) {
					logger.warn("Rejecting order after pricing outage: orderId={}, attempts={}, reason={}",
						payload.orderId(), attempts, ex.getMessage());
					// Update order to REJECTED instead of dead-lettering, and publish it to trade-events
					executorService.rejectNew(orderUuid, payload.accountId(),
						"Pricing service unavailable: " + ex.getMessage(), payload.price(), payload.fee());
					if (ack != null) {
						ack.acknowledge();
					}
					return;
				}

				logger.warn("Retrying order after pricing outage: orderId={}, attempt={}/{}, backoffMillis={}, reason={}",
					payload.orderId(), attempts, MAX_ATTEMPTS, backoffMillis, ex.getMessage());
				sleep(backoffMillis);
				backoffMillis = Math.max(backoffMillis + 1L, Math.round(backoffMillis * BACKOFF_MULTIPLIER));
			} catch (DataAccessException | KafkaException ex) {
				// These are true transient system failures
				attempts++;
				if (attempts >= MAX_ATTEMPTS) {
					logger.warn("Dead-lettering order after retry budget exhausted: orderId={}, attempts={}, reason={}",
						payload.orderId(), attempts, ex.getMessage());
					deadLetterAndAck(record, ex.getMessage(), ack);
					return;
				}

				logger.warn("Retrying order after transient failure: orderId={}, attempt={}/{}, backoffMillis={}, reason={}",
					payload.orderId(), attempts, MAX_ATTEMPTS, backoffMillis, ex.getMessage());
				sleep(backoffMillis);
				backoffMillis = Math.max(backoffMillis + 1L, Math.round(backoffMillis * BACKOFF_MULTIPLIER));
			}
		}
	}

	private void processOnce(OrderPlacedPayload payload, UUID orderUuid) throws PricingUnavailableException {
		if (orderMapper.findOrderById(orderUuid) == null) {
			throw new NoSuchElementException("Order not found in Postgres: " + payload.orderId());
		}

		// A cancelled order never enters the executor: no quote, no fill or reject, no trade event.
		// (The order service already published ORDER_CANCELLED when the customer cancelled it.)
		if (orderMapper.findOrderStatus(orderUuid) == OrderStatus.CANCELLED) {
			logger.info("Skipping cancelled order, not sent to the executor: orderId={}, accountId={}",
				payload.orderId(), payload.accountId());
			return;
		}

		Instrument instrument = instrumentMapper.findBySymbol(payload.symbol());
		OrderSide side = OrderSide.valueOf(payload.side().toUpperCase());

		Order order = null;
		if (instrument != null) {
			order = new Order(
				orderUuid,
				payload.accountId(),
				instrument.getId(),
				side,
				payload.quantity(),
				payload.price(),
				payload.idempotencyKey()
			);
		}

		// Extract quoted price and original fee from ORDER_PLACED event
		java.math.BigDecimal quotedPrice = payload.price();
		java.math.BigDecimal originalFee = payload.fee();
        
		executorService.process(order, quotedPrice, originalFee);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Retry interrupted", ex);
		}
	}

	/**
	 * Dead-letters the record and acknowledges it only once the dead-letter copy is safely in Kafka.
	 * If the send fails or times out (e.g. the broker is down) the record is nacked instead: the
	 * container seeks back and redelivers it after the back-off, so it is neither lost nor skipped.
	 * Redelivery is safe: these paths have no database side effects, or are guarded by the NEW-status
	 * check in the executor.
	 */
	private void deadLetterAndAck(ConsumerRecord<String, String> record, String reason, Acknowledgment ack) {
		boolean written = deadLetter(record, reason);
		if (ack == null) {
			return;
		}
		if (written) {
			ack.acknowledge();
		} else {
			logger.warn("Dead-letter send failed, redelivering record later: topic={}, partition={}, offset={}, backoffMillis={}",
				record.topic(), record.partition(), record.offset(), nackBackoffMs);
			ack.nack(Duration.ofMillis(nackBackoffMs));
		}
	}

	/** Returns true once Kafka acknowledged the dead-letter record within the send timeout. */
	private boolean deadLetter(ConsumerRecord<String, String> record, String reason) {
		RecordHeaders headers = new RecordHeaders();
		headers.add("failure-reason", reason.getBytes(StandardCharsets.UTF_8));
		headers.add("original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
		headers.add("original-partition", Integer.toString(record.partition()).getBytes(StandardCharsets.UTF_8));
		headers.add("original-offset", Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));

		ProducerRecord<String, String> deadLetterRecord = new ProducerRecord<>(
			record.topic() + ".DLT",
			null,
			record.key(),
			record.value(),
			headers
		);
		logger.warn("Publishing dead-letter message: dltTopic={}, originalTopic={}, partition={}, offset={}, reason={}",
			deadLetterRecord.topic(), record.topic(), record.partition(), record.offset(), reason);
		try {
			deadLetterSender.apply(deadLetterRecord).get(deadLetterSendTimeoutMs, TimeUnit.MILLISECONDS);
			return true;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception ex) {
			logger.warn("Dead-letter message not written: dltTopic={}, originalTopic={}, partition={}, offset={}, error={}",
				deadLetterRecord.topic(), record.topic(), record.partition(), record.offset(), ex.getMessage());
			return false;
		}
	}
}
