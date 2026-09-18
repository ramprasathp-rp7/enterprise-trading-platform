package com.tradingplatform.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.OrderPlacedPayload;
import com.tradingplatform.exception.DuplicateDeliveryException;
import com.tradingplatform.exception.OptimisticLockException;
import com.tradingplatform.executor.OrderExecutionResult;
import com.tradingplatform.executor.OrderExecutorService;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.publisher.TradeEventPublisher;
import com.tradingplatform.quote.PricingUnavailableException;

/**
 * Kafka listener for ORDER_PLACED events from the orders topic.
 * 
 * Processing flow:
 * 1. Parse message (poison messages → dead-letter immediately)
 * 2. Load order from Postgres
 * 3. Check for duplicate (already processed → acknowledge & return, don't publish)
 * 4. Execute with retry loop:
 *    - Call OrderExecutorService.process()
 *    - If OptimisticLockException: retry with backoff (up to MAX_LOCK_RETRIES)
 *    - If budget exhausted: dead-letter
 * 5. AFTER execution succeeds: publishTradeEvent() to trade-events topic
 * 6. AFTER publish succeeds: acknowledgment.acknowledge()
 * 
 * This ordering ensures:
 * - No event published if settlement failed or was duplicate
 * - No offset acked if event publish failed
 * - On broker failure: Kafka re-delivers order for retry
 */
@Component
public class OrderPlacedListener {
    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedListener.class);
    
    // Retry configuration for optimistic lock failures
    private static final int MAX_LOCK_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 100L;
    private static final double BACKOFF_MULTIPLIER = 2.0d;

    private final OrderExecutorService executorService;
    private final InstrumentMapper instrumentMapper;
    private final OrderMapper orderMapper;
    private final TradeEventPublisher tradeEventPublisher;
    private final KafkaTemplate<String, String> deadLetterKafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderPlacedListener(
        OrderExecutorService executorService,
        InstrumentMapper instrumentMapper,
        OrderMapper orderMapper,
        TradeEventPublisher tradeEventPublisher,
        KafkaTemplate<String, String> deadLetterKafkaTemplate,
        ObjectMapper objectMapper
    ) {
        this.executorService = executorService;
        this.instrumentMapper = instrumentMapper;
        this.orderMapper = orderMapper;
        this.tradeEventPublisher = tradeEventPublisher;
        this.deadLetterKafkaTemplate = deadLetterKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Listen for ORDER_PLACED events on the orders topic (consumer group: trade-executor).
     * Manual acknowledgment: only ack after successful processing and event publish.
     */
    @KafkaListener(
        topics = "orders",
        groupId = "trade-executor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderPlaced(
        @Payload String recordValue,
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment
    ) {
        int partition = record.partition();
        long offset = record.offset();
        logger.debug("Received order event: topic={}, partition={}, offset={}, key={}", 
            record.topic(), partition, offset, record.key());

        // STEP 1: PARSE MESSAGE (poison → dead-letter immediately, don't retry)
        MessageEnvelope<OrderPlacedPayload> envelope;
        try {
            envelope = objectMapper.readValue(recordValue, new TypeReference<MessageEnvelope<OrderPlacedPayload>>() {});
        } catch (JsonProcessingException ex) {
            logger.warn("Dead-lettering malformed JSON: topic={}, partition={}, offset={}, reason={}",
                record.topic(), partition, offset, ex.getMessage());
            deadLetter(record, "Malformed JSON: " + ex.getMessage());
            acknowledgment.acknowledge();
            return;
        }

        // VALIDATE ENVELOPE (poison → dead-letter)
        if (envelope.eventType() != EventType.ORDER_PLACED) {
            logger.warn("Dead-lettering unexpected event type: topic={}, partition={}, offset={}, eventType={}",
                record.topic(), partition, offset, envelope.eventType());
            deadLetter(record, "Unexpected event type: " + envelope.eventType());
            acknowledgment.acknowledge();
            return;
        }

        OrderPlacedPayload payload = envelope.payload();
        if (payload == null || !validatePayload(payload)) {
            logger.warn("Dead-lettering invalid payload: topic={}, partition={}, offset={}", 
                record.topic(), partition, offset);
            deadLetter(record, "Invalid or missing payload fields");
            acknowledgment.acknowledge();
            return;
        }

        // Parse order ID
        UUID orderUuid;
        try {
            orderUuid = UUID.fromString(payload.orderId());
        } catch (IllegalArgumentException ex) {
            logger.warn("Dead-lettering invalid order ID: topic={}, partition={}, offset={}, orderId={}",
                record.topic(), partition, offset, payload.orderId());
            deadLetter(record, "Invalid order ID: " + payload.orderId());
            acknowledgment.acknowledge();
            return;
        }

        logger.debug("Processing order event: orderId={}, accountId={}, partition={}, offset={}",
            payload.orderId(), payload.accountId(), partition, offset);

        // STEP 2: LOAD ORDER (poison if not found)
        Order order = orderMapper.findOrderById(orderUuid);
        if (order == null) {
            logger.warn("Dead-lettering order not found in database: orderId={}",  payload.orderId());
            deadLetter(record, "Order not found in database");
            acknowledgment.acknowledge();
            return;
        }

        // STEP 3: DUPLICATE CHECK
        if (order.getStatus() != OrderStatus.NEW) {
            logger.info("Duplicate delivery detected: order already processed: orderId={}, status={}, partition={}, offset={}",
                payload.orderId(), order.getStatus(), partition, offset);
            // Order already settled, don't process again, don't publish event, just ack
            acknowledgment.acknowledge();
            return;
        }

        // STEP 4: EXECUTE WITH RETRY LOOP
        executeOrderWithRetry(order, payload, record, acknowledgment);
    }

    /**
     * Execute order with exponential backoff retry on optimistic lock conflicts.
     * 
     * On OptimisticLockException: refresh account/position and retry (transient failure).
     * On PricingUnavailableException: retry (Fauxnance API transient failure).
     * On other exceptions: dead-letter (poison message or unrecoverable failure).
     * 
     * After successful execution:
     * 1. Publish trade event to trade-events
     * 2. Acknowledge offset
     */
    private void executeOrderWithRetry(
        Order order,
        OrderPlacedPayload payload,
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment
    ) {
        int attempt = 0;
        long backoffMillis = INITIAL_BACKOFF_MILLIS;

        while (attempt < MAX_LOCK_RETRIES) {
            attempt++;
            try {
                logger.debug("Executing order: orderId={}, attempt={}/{}", 
                    payload.orderId(), attempt, MAX_LOCK_RETRIES);

                // Reconstruct order object from payload
                Instrument instrument = instrumentMapper.findBySymbol(payload.symbol());
                OrderSide side = OrderSide.valueOf(payload.side().toUpperCase());
                Order orderToExecute = (instrument != null)
                    ? new Order(
                        payload.accountId(),
                        instrument.getId(),
                        side,
                        payload.quantity(),
                        payload.price(),
                        payload.idempotencyKey()
                    )
                    : null;

                // Execute order (returns result with status, price, reason, isDuplicate)
                OrderExecutionResult result = executorService.process(
                    payload.orderId(),
                    payload.accountId(),
                    orderToExecute
                );

                // Check for duplicate (order already settled during execution)
                if (result.isDuplicate()) {
                    logger.info("Order settlement returned duplicate during execution: orderId={}", 
                        payload.orderId());
                    // Don't publish event (already published by earlier delivery)
                    acknowledgment.acknowledge();
                    return;
                }

                // STEP 5: PUBLISH EVENT (AFTER execution succeeds but BEFORE ack)
                publishTradeEvent(payload, result);

                // STEP 6: ACKNOWLEDGE (AFTER publish succeeds)
                acknowledgment.acknowledge();
                logger.info("Order processed successfully: orderId={}, status={}, price={}, attempt={}",
                    payload.orderId(), result.finalStatus(), result.executedPrice(), attempt);
                return;

            } catch (OptimisticLockException e) {
                // TRANSIENT FAILURE: Retry with backoff
                if (attempt < MAX_LOCK_RETRIES) {
                    logger.warn("Optimistic lock conflict on attempt {}/{}, retrying in {}ms: orderId={}",
                        attempt, MAX_LOCK_RETRIES, backoffMillis, payload.orderId());
                    try {
                        Thread.sleep(backoffMillis);
                        backoffMillis = (long) (backoffMillis * BACKOFF_MULTIPLIER);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Thread interrupted during backoff: orderId={}", payload.orderId());
                        deadLetter(record, "Thread interrupted during retry: " + ie.getMessage());
                        acknowledgment.acknowledge();
                        return;
                    }
                } else {
                    // BUDGET EXHAUSTED: Dead-letter
                    logger.error("Optimistic lock retry budget exhausted after {} attempts: orderId={}",
                        MAX_LOCK_RETRIES, payload.orderId());
                    deadLetter(record, "Optimistic lock retry budget exhausted: " + e.getMessage());
                    acknowledgment.acknowledge();
                    return;
                }

            } catch (PricingUnavailableException e) {
                // TRANSIENT FAILURE: Retry or fail based on context
                if (attempt < MAX_LOCK_RETRIES) {
                    logger.warn("Pricing unavailable, retrying attempt {}/{}: orderId={}",
                        attempt, MAX_LOCK_RETRIES, payload.orderId());
                    try {
                        Thread.sleep(backoffMillis);
                        backoffMillis = (long) (backoffMillis * BACKOFF_MULTIPLIER);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        deadLetter(record, "Thread interrupted during pricing retry: " + ie.getMessage());
                        acknowledgment.acknowledge();
                        return;
                    }
                } else {
                    // BUSINESS DECISION: Pricing unavailable is treated as rejection (not a poison message)
                    logger.warn("Pricing unavailable, rejecting order: orderId={}", payload.orderId());
                    try {
                        tradeEventPublisher.publishTradeEvent(
                            UUID.fromString(payload.orderId()),
                            payload.accountId(),
                            OrderStatus.REJECTED,
                            null,
                            "Pricing unavailable: " + e.getMessage(),
                            Instant.now()
                        );
                        acknowledgment.acknowledge();
                    } catch (Exception publishEx) {
                        logger.error("Failed to publish rejection event: orderId={}", payload.orderId());
                        deadLetter(record, "Failed to publish rejection event: " + publishEx.getMessage());
                        acknowledgment.acknowledge();
                    }
                    return;
                }

            } catch (DuplicateDeliveryException e) {
                // DUPLICATE: Should not happen at this stage (already checked above)
                logger.warn("Duplicate delivery detected during execution: orderId={}", payload.orderId());
                acknowledgment.acknowledge();
                return;

            } catch (Exception e) {
                // OTHER EXCEPTIONS: Likely poison message or unrecoverable failure
                logger.error("Unexpected error processing order: orderId={}, error={}",
                    payload.orderId(), e.getMessage(), e);
                deadLetter(record, "Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                acknowledgment.acknowledge();
                return;
            }
        }

        // Should not reach here
        logger.error("Unexpected: reached end of retry loop: orderId={}", payload.orderId());
        deadLetter(record, "Internal error: unexpected end of retry loop");
        acknowledgment.acknowledge();
    }

    /**
     * Publish ORDER_FILLED or ORDER_REJECTED event to trade-events topic.
     * Triggered AFTER successful settlement and BEFORE offset acknowledgment.
     * 
     * If publish fails, exception propagates to caller (retry loop).
     */
    private void publishTradeEvent(OrderPlacedPayload payload, OrderExecutionResult result) {
        try {
            tradeEventPublisher.publishTradeEvent(
                UUID.fromString(payload.orderId()),
                payload.accountId(),
                result.finalStatus(),
                result.executedPrice(),
                result.rejectReason(),
                Instant.now()
            );
            logger.debug("Published trade event: orderId={}, status={}, eventType={}",
                payload.orderId(), result.finalStatus(),
                result.finalStatus() == OrderStatus.FILLED ? "ORDER_FILLED" : "ORDER_REJECTED");
        } catch (RuntimeException e) {
            logger.error("Failed to publish trade event: orderId={}, error={}",
                payload.orderId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Send message to dead-letter topic with failure reason in headers.
     * Used for poison messages (malformed JSON, missing fields, invalid IDs) and
     * for retry-exhausted messages (after MAX_LOCK_RETRIES exceeded).
     */
    private void deadLetter(ConsumerRecord<String, String> record, String reason) {
        try {
            RecordHeaders headers = new RecordHeaders();
            headers.add("failure-reason", reason.getBytes(StandardCharsets.UTF_8));
            headers.add("original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
            headers.add("original-partition", Integer.toString(record.partition()).getBytes(StandardCharsets.UTF_8));
            headers.add("original-offset", Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));
            headers.add("original-timestamp", Long.toString(record.timestamp()).getBytes(StandardCharsets.UTF_8));

            ProducerRecord<String, String> deadLetterRecord = new ProducerRecord<>(
                record.topic() + ".DLT",
                null,
                record.key(),
                record.value(),
                headers
            );
            deadLetterKafkaTemplate.send(deadLetterRecord);
            logger.info("Dead-lettered message: dltTopic={}, originalPartition={}, offset={}, reason={}",
                deadLetterRecord.topic(), record.partition(), record.offset(), reason);
        } catch (Exception e) {
            logger.error("Failed to send dead-letter message: reason={}, error={}",
                reason, e.getMessage(), e);
        }
    }

    /**
     * Validate OrderPlacedPayload has all required fields.
     */
    private boolean validatePayload(OrderPlacedPayload payload) {
        return payload.orderId() != null && !payload.orderId().isBlank()
            && payload.accountId() != null
            && payload.symbol() != null && !payload.symbol().isBlank()
            && payload.side() != null && !payload.side().isBlank()
            && payload.quantity() != null
            && payload.price() != null
            && payload.idempotencyKey() != null && !payload.idempotencyKey().isBlank();
    }
}
