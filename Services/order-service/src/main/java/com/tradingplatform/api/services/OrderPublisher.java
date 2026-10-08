package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.OrderPlacedPayload;
import com.tradingplatform.api.events.Source;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing order events to Kafka.
 *
 * Publishes ORDER_PLACED events to the 'orders' topic after an order has been
 * successfully persisted to the database. The event includes all order details
 * needed by the Trade Executor for processing.
 *
 * Publishes ORDER_CANCELLED events to the 'trade-events' topic when a customer cancels
 * a NEW order, so cancellations sit alongside fills and rejections on the topic that
 * customer notifications are built from. The executor does not consume 'trade-events',
 * and it skips orders that are already CANCELLED when their ORDER_PLACED arrives.
 *
 * Publishing is fire-and-forget: if Kafka is unavailable, the exception is logged
 * but not rethrown, since the order is already safely persisted in the database.
 * The order's event_pending flag stays set until the broker acknowledges the event
 * for its status; OrderReplayScheduler republishes pending ORDER_PLACED and
 * ORDER_CANCELLED events once the broker is back.
 */
@Service
public class OrderPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OrderPublisher.class);
    private static final String ORDERS_TOPIC = "orders";
    private static final String TRADE_EVENTS_TOPIC = "trade-events";
    static final String CANCELLED_BY_CUSTOMER = "CANCELLED_BY_CUSTOMER";

    private final KafkaTemplate<String, MessageEnvelope<?>> kafkaTemplate;
    private final OrderMapper orderMapper;


    public OrderPublisher(KafkaTemplate<String, MessageEnvelope<?>> kafkaTemplate,
                          ObjectMapper kafkaObjectMapper,
                          OrderMapper orderMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderMapper = orderMapper;
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
     * - The order keeps event_pending set, so OrderReplayScheduler republishes it later
     *
     * On acknowledgement the order's event_pending is cleared (off the Kafka I/O thread).
     *
     * @param order The Order entity that was just persisted at NEW status
     * @param instrument The Instrument for the order (needed to get symbol)
     * @return completes with true once Kafka acknowledged the event, false if publishing failed;
     *         never completes exceptionally. Placement ignores it; the replay job waits on it.
     */
    public CompletableFuture<Boolean> publishOrderPlaced(Order order, Instrument instrument) {
        try {
            // Build the ORDER_PLACED payload
            OrderPlacedPayload payload = new OrderPlacedPayload(
                order.getId().toString(),           // orderId as string UUID
                order.getAccountId(),               // accountId (numeric)
                instrument.getSymbol(),             // symbol from instrument
                order.getSide().name(),             // side: BUY or SELL
                order.getQuantity(),                // quantity
                order.getPrice(),                   // limit price (quoted price)
                order.getFee(),                     // fee calculated at order time
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
            return kafkaTemplate.send(ORDERS_TOPIC, messageKey, envelope)
                .handleAsync((result, exception) -> {
                    if (exception == null) {
                        logger.info("ORDER_PLACED event published: orderId={}, accountId={}, partition={}, offset={}",
                            order.getId(),
                            order.getAccountId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                        markEventPublished(order.getId(), OrderStatus.NEW);
                        return true;
                    }
                    logger.warn("Failed to publish ORDER_PLACED event: orderId={}, accountId={}, error={}",
                        order.getId(),
                        order.getAccountId(),
                        exception.getMessage(), exception);
                    return false;
                });

        } catch (Exception exception) {
            // Log but do not rethrow: order is already in database
            logger.warn("Error preparing ORDER_PLACED event for orderId={}: {}",
                order.getId(),
                exception.getMessage(), exception);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Clears event_pending for the event about the given status. A failed mark only means the replay
     * job publishes the event again, and every consumer ignores the duplicate.
     */
    private void markEventPublished(UUID orderId, OrderStatus status) {
        try {
            orderMapper.markEventPublished(orderId, status);
        } catch (Exception exception) {
            logger.warn("{} event published but event_pending not cleared: orderId={}, error={}",
                status, orderId, exception.getMessage());
        }
    }

    /**
     * Event id for an order's outcome event, the same on every publish of it, so consumers that
     * de-duplicate on eventId (customer notifications) ignore a replayed copy.
     */
    static String outcomeEventId(EventType eventType, UUID orderId) {
        return UUID.nameUUIDFromBytes((eventType + ":" + orderId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Publishes an ORDER_CANCELLED event to the 'trade-events' topic after a NEW order was cancelled.
     *
     * Keyed by accountId, like the executor's ORDER_FILLED / ORDER_REJECTED events, so all outcomes
     * of an account stay in order. The payload carries the order's symbol, side, quantity and price,
     * and the stored cancel time, so a replayed event is identical to the live one (same eventId too).
     *
     * Fire-and-forget, like ORDER_PLACED: the cancellation is already committed in the database,
     * so a Kafka failure is logged and not rethrown, and the order keeps event_pending set for
     * OrderReplayScheduler. On acknowledgement event_pending is cleared.
     *
     * @param orderId The cancelled order's id
     * @param order The cancelled order (for account, side, quantity, price and fee)
     * @param instrument The order's instrument (for the symbol)
     * @param cancelledOn When the customer cancelled (orders.cancelled_on); now if unknown
     * @return completes with true once Kafka acknowledged the event, false if publishing failed;
     *         never completes exceptionally
     */
    public CompletableFuture<Boolean> publishOrderCancelled(UUID orderId, Order order, Instrument instrument,
                                                           Instant cancelledOn) {
        try {
            Instant cancelledAt = cancelledOn != null ? cancelledOn : Instant.now();
            TradeEventPayload payload = new TradeEventPayload(
                orderId.toString(),
                order.getAccountId(),
                instrument.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getPrice(),
                order.getPrice(),               // quotedPrice: the limit price it was placed at
                null,                           // executedPrice: never executed
                order.getFee(),                 // originalFee
                "CANCELLED",
                CANCELLED_BY_CUSTOMER,
                null,                           // cashDelta: nothing settled
                null,                           // positionQuantityAfter: position unchanged
                null,                           // averageCostAfter: position unchanged
                cancelledAt
            );

            MessageEnvelope<TradeEventPayload> envelope = new MessageEnvelope<>(
                outcomeEventId(EventType.ORDER_CANCELLED, orderId),
                EventType.ORDER_CANCELLED,
                cancelledAt,
                Source.TRADE_API,
                1,
                payload
            );

            String messageKey = String.valueOf(order.getAccountId());
            return kafkaTemplate.send(TRADE_EVENTS_TOPIC, messageKey, envelope)
                .handleAsync((result, exception) -> {
                    if (exception == null) {
                        logger.info("ORDER_CANCELLED event published: orderId={}, accountId={}, partition={}, offset={}",
                            orderId,
                            order.getAccountId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                        markEventPublished(orderId, OrderStatus.CANCELLED);
                        return true;
                    }
                    logger.warn("Failed to publish ORDER_CANCELLED event: orderId={}, accountId={}, error={}",
                        orderId,
                        order.getAccountId(),
                        exception.getMessage(), exception);
                    return false;
                });

        } catch (Exception exception) {
            // Log but do not rethrow: the cancellation is already in the database
            logger.warn("Error preparing ORDER_CANCELLED event for orderId={}: {}",
                orderId,
                exception.getMessage(), exception);
            return CompletableFuture.completedFuture(false);
        }
    }
}
