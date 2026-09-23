package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.OrderPlacedPayload;
import com.tradingplatform.api.events.Source;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for publishing order events to Kafka.
 *
 * Publishes ORDER_PLACED events to the 'orders' topic after an order has been
 * successfully persisted to the database. The event includes all order details
 * needed by the Trade Executor for processing.
 *
 * Publishing is fire-and-forget: if Kafka is unavailable, the exception is logged
 * but not rethrown, since the order is already safely persisted in the database
 * and can be replayed by the executor later.
 */
@Service
public class OrderPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OrderPublisher.class);
    private static final String ORDERS_TOPIC = "orders";

    private final KafkaTemplate<String, MessageEnvelope<?>> kafkaTemplate;
    private final ObjectMapper kafkaObjectMapper;

    public OrderPublisher(KafkaTemplate<String, MessageEnvelope<?>> kafkaTemplate,
                          ObjectMapper kafkaObjectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaObjectMapper = kafkaObjectMapper;
    }

    /**
     * Publishes an ORDER_PLACED event to the 'orders' topic.
     *
     * The event is keyed by accountId (as a string) to ensure per-account ordering:
     * orders for the same account are always processed in the order received,
     * while orders for different accounts can be processed in parallel.
     *
     * Publishing is fire-and-forget. If Kafka is unavailable:
     * - The exception is logged at WARN level
     * - The method does not rethrow, since the order is already in the database
     * - The Trade Executor can replay the order from the order table later
     *
     * @param order The Order entity that was just persisted at NEW status
     * @param instrument The Instrument for the order (needed to get symbol)
     */
    public void publishOrderPlaced(Order order, Instrument instrument) {
        try {
            // Build the ORDER_PLACED payload
            OrderPlacedPayload payload = new OrderPlacedPayload(
                order.getId().toString(),           // orderId as string UUID
                order.getAccountId(),               // accountId (numeric)
                instrument.getSymbol(),             // symbol from instrument
                order.getSide().name(),             // side: BUY or SELL
                order.getQuantity(),                // quantity
                order.getPrice(),                   // limit price
                order.getIdempotencyKey(),          // client's idempotency key
                order.getCreatedOn()                // when order was recorded
            );

            // Create the message envelope
            MessageEnvelope<OrderPlacedPayload> envelope = new MessageEnvelope<>(
                UUID.randomUUID().toString(),       // unique eventId per message
                EventType.ORDER_PLACED,             // event type
                Instant.now(),                      // when published (RFC 3339 UTC)
                Source.TRADE_API,                   // producer: trade-api service
                1,                                  // schemaVersion
                payload
            );

            // Send to Kafka with key = accountId (as string) for per-account partition routing
            String messageKey = String.valueOf(order.getAccountId());
            kafkaTemplate.send(ORDERS_TOPIC, messageKey, envelope)
                .whenComplete((result, exception) -> {
                    if (exception == null) {
                        logger.info("ORDER_PLACED event published: orderId={}, accountId={}, partition={}, offset={}",
                            order.getId(),
                            order.getAccountId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    } else {
                        logger.warn("Failed to publish ORDER_PLACED event: orderId={}, accountId={}, error={}",
                            order.getId(),
                            order.getAccountId(),
                            exception.getMessage(), exception);
                    }
                });

        } catch (Exception exception) {
            // Log but do not rethrow: order is already in database
            logger.warn("Error preparing ORDER_PLACED event for orderId={}: {}",
                order.getId(),
                exception.getMessage(), exception);
        }
    }
}

