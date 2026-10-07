package com.tradingplatform.notifications.service;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.notifications.channel.NotificationDispatcher;
import com.tradingplatform.notifications.mapper.OrderNotificationMapper;
import com.tradingplatform.notifications.model.OrderDetails;
import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.preferences.ResolvedAlertChannels;

/**
 * Turns order outcomes on the trade-events topic into customer notifications.
 *
 * The Trade Executor publishes ORDER_FILLED and ORDER_REJECTED (ORDER_CANCELLED is also accepted)
 * to `trade-events`, keyed by account. Each event is stored in ORDER_NOTIFICATIONS, enriched with
 * the order's symbol, side, quantity and limit price, and sent on the channels the customer chose
 * in their preferences (NotificationDispatcher): IN_APP rows appear in the UI bell through
 * GET /api/v1/accounts/{accountId}/order-notifications; EMAIL and SMS go to the channel senders
 * (stubs for now). This consumer is the only source of order success and failure notifications.
 *
 * Email and SMS are sent only when the event is stored for the first time, so a redelivered
 * event never sends them twice.
 *
 * Delivery and errors (at-least-once, manual offset commit):
 * - Redelivered events are stored once (event_id is unique)
 * - Malformed or irrelevant messages are logged and skipped (offset committed)
 * - Database failures are rethrown so the container retries the message
 *
 * Related:
 * @see OrderNotificationService for reads
 * @see TradeEventPayload for the payload
 */
@Service
public class TradeEventNotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TradeEventNotificationConsumer.class);

    /** Event type to the order status it reports */
    private static final Map<EventType, String> STATUS_BY_EVENT = Map.of(
        EventType.ORDER_FILLED, "FILLED",
        EventType.ORDER_REJECTED, "REJECTED",
        EventType.ORDER_CANCELLED, "CANCELLED"
    );

    private final ObjectMapper objectMapper;
    private final OrderNotificationMapper notificationMapper;
    private final NotificationDispatcher dispatcher;

    public TradeEventNotificationConsumer(ObjectMapper objectMapper, OrderNotificationMapper notificationMapper,
                                          NotificationDispatcher dispatcher) {
        this.objectMapper = objectMapper;
        this.notificationMapper = notificationMapper;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = "trade-events", groupId = "order-notifications")
    public void handleTradeEvent(String messageValue, Acknowledgment ack) {
        MessageEnvelope<TradeEventPayload> envelope;
        try {
            envelope = objectMapper.readValue(messageValue, new TypeReference<MessageEnvelope<TradeEventPayload>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("Skipping malformed trade event: {}", e.getOriginalMessage());
            acknowledge(ack);
            return;
        }

        OrderNotification notification = toNotification(envelope);
        if (notification == null) {
            acknowledge(ack);
            return;
        }

        // Where does this customer want to hear about it? (EMAIL / SMS / IN_APP)
        ResolvedAlertChannels channels = dispatcher.resolveChannels(notification.getAccountId());
        dispatcher.applyChannels(notification, channels);

        try {
            int stored = notificationMapper.insertIgnoringDuplicate(notification);
            if (stored == 0) {
                logger.debug("Trade event already stored: eventId={}", notification.getEventId());
            } else {
                logger.info("Order notification stored: accountId={}, orderId={}, status={}, channels={}",
                    notification.getAccountId(), notification.getOrderId(), notification.getStatus(),
                    notification.getChannels());
                dispatcher.sendExternal(notification, channels);
            }
            acknowledge(ack);
        } catch (RuntimeException e) {
            // Database trouble: leave the offset uncommitted so the message is retried
            logger.error("Could not store order notification for eventId={}", notification.getEventId(), e);
            throw e;
        }
    }

    /** Builds the notification, or returns null for events that are not order outcomes or are incomplete. */
    OrderNotification toNotification(MessageEnvelope<TradeEventPayload> envelope) {
        if (envelope == null || envelope.eventType() == null || !STATUS_BY_EVENT.containsKey(envelope.eventType())) {
            logger.debug("Skipping non-outcome trade event: {}", envelope == null ? null : envelope.eventType());
            return null;
        }
        TradeEventPayload payload = envelope.payload();
        if (payload == null || payload.orderId() == null || payload.accountId() == null || envelope.eventId() == null) {
            logger.warn("Skipping trade event without order, account or event id: eventId={}",
                envelope.eventId());
            return null;
        }

        OrderNotification n = new OrderNotification();
        n.setEventId(envelope.eventId());
        n.setAccountId(payload.accountId());
        n.setOrderId(payload.orderId());
        n.setEventType(envelope.eventType().name());
        n.setStatus(STATUS_BY_EVENT.get(envelope.eventType()));
        n.setExecutedPrice(payload.executedPrice());
        n.setReason(payload.reason());
        Instant occurredAt = payload.executedOn() != null ? payload.executedOn() : envelope.eventTime();
        n.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());

        // Trade events omit symbol, side and quantity: take them from the event, else from the order
        n.setSymbol(payload.symbol());
        n.setSide(payload.side());
        n.setQuantity(payload.quantity());
        n.setLimitPrice(payload.price());
        if (n.getSymbol() == null || n.getSide() == null || n.getQuantity() == null || n.getLimitPrice() == null) {
            OrderDetails order = lookUpOrder(payload.orderId());
            if (order != null) {
                if (n.getSymbol() == null) n.setSymbol(order.getSymbol());
                if (n.getSide() == null) n.setSide(order.getSide());
                if (n.getQuantity() == null) n.setQuantity(order.getQuantity());
                if (n.getLimitPrice() == null) n.setLimitPrice(order.getLimitPrice());
            }
        }
        return n;
    }

    private OrderDetails lookUpOrder(String orderId) {
        try {
            return notificationMapper.findOrderDetails(orderId);
        } catch (RuntimeException e) {
            // Not a UUID or the lookup failed: store the notification without the order details
            logger.warn("Could not look up order {} for its notification: {}", orderId, e.getMessage());
            return null;
        }
    }

    private static void acknowledge(Acknowledgment ack) {
        if (ack != null) {
            ack.acknowledge();
        }
    }
}
