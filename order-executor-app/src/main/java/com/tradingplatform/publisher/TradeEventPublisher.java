package com.tradingplatform.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.TradeEventPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for publishing trade events to the trade-events topic.
 * Handles serialization and error handling for ORDER_FILLED and ORDER_REJECTED events.
 */
@Service
public class TradeEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(TradeEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TradeEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a trade event (ORDER_FILLED or ORDER_REJECTED) to trade-events topic.
     * 
     * Message structure:
     * - Topic: trade-events
     * - Key: accountId (ensures per-account ordering in partition)
     * - Value: MessageEnvelope containing TradeEventPayload
     * - EventType: ORDER_FILLED or ORDER_REJECTED (determined by status)
     * 
     * @param orderId The order ID
     * @param accountId The account ID (used as Kafka key)
     * @param status The final status (FILLED or REJECTED)
     * @param executedPrice The executed price (null if rejected)
     * @param rejectReason The rejection reason (null if filled)
     * @param executedOn The timestamp when order was executed
     * @throws RuntimeException if serialization or publishing fails
     */
    public void publishTradeEvent(
        UUID orderId,
        long accountId,
        OrderStatus status,
        BigDecimal executedPrice,
        String rejectReason,
        Instant executedOn
    ) {
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
            executedPrice,
            status.name(),
            rejectReason,
            null,  // cashDelta - not calculated by executor
            null,  // positionQuantityAfter - not returned by executor
            null,  // averageCostAfter - not returned by executor
            executedOn
        );

        // Wrap in message envelope
        MessageEnvelope<TradeEventPayload> envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            eventType,
            Instant.now(),
            com.tradingplatform.events.Source.TRADE_EXECUTOR,
            1,
            payload
        );

        // Serialize and publish
        try {
            String messageJson = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(
                new ProducerRecord<>(
                    "trade-events",
                    String.valueOf(accountId),  // Key: account ID for partitioning
                    messageJson
                )
            );
            logger.debug("Published trade event: orderId={}, accountId={}, status={}, eventType={}",
                orderId, accountId, status, eventType);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize trade event: orderId={}, accountId={}, status={}",
                orderId, accountId, status, e);
            throw new RuntimeException("Trade event serialization failed: " + e.getMessage(), e);
        }
    }
}
