package com.tradingplatform.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.PendingOutcome;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.TradeEventPayload;
import com.tradingplatform.mappers.OrderMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing trade events to the trade-events topic.
 * Handles serialization and error handling for ORDER_FILLED and ORDER_REJECTED events.
 *
 * Called inside the settlement transaction, the event is serialized there (a failure rolls the
 * settlement back) and sent after commit, so the order row lock is not held while Kafka is slow
 * and no event announces a settlement that rolled back. Outside a transaction (replay) it is sent
 * at once.
 *
 * Fire-and-forget: a failed send is logged and the order keeps orders.event_pending set (set by
 * updateOrderStatus with the outcome). It is cleared once Kafka acknowledges the event, and
 * OutcomeReplayScheduler republishes events still pending. Every publish of an order's event has
 * the same eventId, so notifications ignore a replayed copy; fee deduction is idempotent per order.
 */
@Service
public class TradeEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(TradeEventPublisher.class);

    private static final String TRADE_EVENTS_TOPIC = "trade-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;

    public TradeEventPublisher(@Qualifier("tradeEventsKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper,
                               OrderMapper orderMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * Publish a trade event (ORDER_FILLED or ORDER_REJECTED) to trade-events topic. Used by the executor
     * for a fresh outcome and by OutcomeReplayScheduler to republish one still pending.
     * 
     * Message structure:
     * - Topic: trade-events
     * - Key: accountId (ensures per-account ordering in partition)
     * - Value: MessageEnvelope containing TradeEventPayload
     * - EventType: ORDER_FILLED or ORDER_REJECTED (determined by status)
     * 
     * The outcome price is published as the quoted price and its fee as the original fee. The fee
     * deduction service settles the BUY/SELL transaction and the fee in account currency at the
     * outcome fill FX rate (null if rejected).
     *
     * @return completes with true once Kafka acknowledged the event, false if publishing failed;
     *         inside a transaction it completes after commit, and never if the transaction rolls back
     * @throws RuntimeException if serialization fails
     */
    public CompletableFuture<Boolean> publishOutcome(PendingOutcome outcome) {
        UUID orderId = outcome.id();
        long accountId = outcome.accountId();
        OrderStatus status = outcome.status();

        // Determine event type based on status
        EventType eventType = (status == OrderStatus.FILLED)
            ? EventType.ORDER_FILLED
            : EventType.ORDER_REJECTED;

        // Create payload (only required fields; rest can be null)
        TradeEventPayload payload = new TradeEventPayload(
            orderId.toString(),
            accountId,
            null,  // symbol - not needed for event publishing
            null,  // side - not needed for event publishing
            null,  // quantity - not needed for event publishing
            null,  // price - not needed for event publishing
            outcome.price(),  // quotedPrice from ORDER_PLACED
            outcome.executedPrice(),
            outcome.fee(),  // originalFee from ORDER_PLACED
            status.name(),
            outcome.rejectedReason(),
            null,  // cashDelta - not calculated by executor
            null,  // positionQuantityAfter - not returned by executor
            null,  // averageCostAfter - not returned by executor
            outcome.executedOn(),
            outcome.fillFxRate()
        );

        // Wrap in message envelope
        MessageEnvelope<TradeEventPayload> envelope = new MessageEnvelope<>(
            outcomeEventId(eventType, orderId),
            eventType,
            Instant.now(),
            com.tradingplatform.events.Source.TRADE_EXECUTOR,
            1,
            payload
        );

        // Serialize now, so a failure still rolls back the settlement it would describe
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize trade event: orderId={}, accountId={}, status={}",
                orderId, accountId, status, e);
            throw new RuntimeException("Trade event serialization failed: " + e.getMessage(), e);
        }
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
            TRADE_EVENTS_TOPIC,
            String.valueOf(accountId),  // Key: account ID for partitioning
            messageJson
        );

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return send(producerRecord, orderId, accountId, status, eventType);
        }
        CompletableFuture<Boolean> published = new CompletableFuture<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(producerRecord, orderId, accountId, status, eventType).thenAccept(published::complete);
            }
        });
        return published;
    }

    /** Sends the event; never throws and never completes exceptionally. */
    private CompletableFuture<Boolean> send(ProducerRecord<String, String> producerRecord, UUID orderId, long accountId,
                                            OrderStatus status, EventType eventType) {
        try {
            return kafkaTemplate.send(producerRecord).handleAsync((result, exception) -> {
                if (exception == null) {
                    logger.info("Published trade event: orderId={}, accountId={}, status={}, eventType={}",
                        orderId, accountId, status, eventType);
                    markEventPublished(orderId, status);
                    return true;
                }
                logger.warn("Failed to publish trade event, left pending for replay: orderId={}, accountId={}, eventType={}, error={}",
                    orderId, accountId, eventType, exception.getMessage());
                return false;
            });
        } catch (Exception exception) {
            logger.warn("Failed to publish trade event, left pending for replay: orderId={}, accountId={}, eventType={}, error={}",
                orderId, accountId, eventType, exception.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /** A failed mark only means the replay job publishes the event again; consumers ignore the duplicate. */
    private void markEventPublished(UUID orderId, OrderStatus status) {
        try {
            orderMapper.markEventPublished(orderId, status);
        } catch (Exception exception) {
            logger.warn("Trade event published but event_pending not cleared: orderId={}, status={}, error={}",
                orderId, status, exception.getMessage());
        }
    }

    /** Event id for an order's outcome event, the same on every publish of it (live or replayed). */
    static String outcomeEventId(EventType eventType, UUID orderId) {
        return UUID.nameUUIDFromBytes((eventType + ":" + orderId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
