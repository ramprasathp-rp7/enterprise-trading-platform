package com.tradingplatform.kafka;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
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

	private final OrderExecutorService executorService;
	private final InstrumentMapper instrumentMapper;
	private final OrderMapper orderMapper;
	private final java.util.function.Consumer<org.apache.kafka.clients.producer.ProducerRecord<String, String>> deadLetterSender;
	private final ObjectMapper objectMapper;

	@Autowired
	public OrderPlacedListener(
		OrderExecutorService executorService,
		InstrumentMapper instrumentMapper,
		OrderMapper orderMapper,
		KafkaTemplate<String, String> deadLetterKafkaTemplate,
		ObjectMapper objectMapper
	) {
		this(executorService, instrumentMapper, orderMapper, r -> deadLetterKafkaTemplate.send(r), objectMapper);
	}

	public OrderPlacedListener(
		OrderExecutorService executorService,
		InstrumentMapper instrumentMapper,
		OrderMapper orderMapper,
		java.util.function.Consumer<org.apache.kafka.clients.producer.ProducerRecord<String, String>> deadLetterSender,
		ObjectMapper objectMapper
	) {
		this.executorService = executorService;
		this.instrumentMapper = instrumentMapper;
		this.orderMapper = orderMapper;
		this.deadLetterSender = deadLetterSender;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = "orders", groupId = "trade-executor")
	public void onOrderPlaced(ConsumerRecord<String, String> record) {
		MessageEnvelope<OrderPlacedPayload> envelope;
		try {
			envelope = objectMapper.readValue(record.value(), new TypeReference<MessageEnvelope<OrderPlacedPayload>>() {});
		} catch (JsonProcessingException ex) {
			logger.warn("Dead-lettering malformed order message: topic={}, partition={}, offset={}, reason={}",
				record.topic(), record.partition(), record.offset(), ex.getMessage());
			deadLetter(record, "Malformed JSON: " + ex.getMessage());
			return;
		}

		if (envelope.eventType() != EventType.ORDER_PLACED) {
			logger.warn("Dead-lettering unexpected event type: topic={}, partition={}, offset={}, eventType={}",
				record.topic(), record.partition(), record.offset(), envelope.eventType());
			deadLetter(record, "Unexpected event type: " + envelope.eventType());
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
			deadLetter(record, "Missing required order field");
			return;
		}

		UUID orderUuid;
		try {
			orderUuid = UUID.fromString(payload.orderId());
		} catch (IllegalArgumentException ex) {
			logger.warn("Dead-lettering order with invalid order identifier: topic={}, partition={}, offset={}, orderId={}",
				record.topic(), record.partition(), record.offset(), payload.orderId());
			deadLetter(record, "Invalid order identifier: " + payload.orderId());
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
				return;
			} catch (NoSuchElementException | IllegalArgumentException ex) {
				logger.warn("Dead-lettering order after terminal failure: orderId={}, reason={}", payload.orderId(), ex.getMessage());
				deadLetter(record, ex.getMessage());
				return;
			} catch (PricingUnavailableException | DataAccessException | KafkaException ex) {

				attempts++;
				if (attempts >= MAX_ATTEMPTS) {
					logger.warn("Dead-lettering order after retry budget exhausted: orderId={}, attempts={}, reason={}",
						payload.orderId(), attempts, ex.getMessage());
					deadLetter(record, ex.getMessage());
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

	private void deadLetter(ConsumerRecord<String, String> record, String reason) {
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
		deadLetterSender.accept(deadLetterRecord);
	}
}
