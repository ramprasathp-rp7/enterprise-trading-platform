package com.tradingplatform.notifications.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tradingplatform.notifications.model.OrderNotification;

/**
 * An order outcome for the UI notification bell.
 *
 * @param id            increasing id; pass the largest seen as ?after= to get only newer ones
 * @param orderId       the order
 * @param eventType     ORDER_FILLED, ORDER_REJECTED or ORDER_CANCELLED (as on trade-events)
 * @param status        FILLED, REJECTED or CANCELLED
 * @param symbol        instrument symbol (null if the order could not be found)
 * @param side          BUY or SELL (null if the order could not be found)
 * @param quantity      order quantity (null if the order could not be found)
 * @param price         the order's limit price (null if the order could not be found)
 * @param executedPrice fill price (null unless FILLED)
 * @param reason        why the order was rejected (null unless REJECTED)
 * @param occurredAt    when the order was executed
 */
public record OrderNotificationResponse(
    long id,
    String orderId,
    String eventType,
    String status,
    String symbol,
    String side,
    Integer quantity,
    BigDecimal price,
    BigDecimal executedPrice,
    String reason,
    Instant occurredAt
) {
    public static OrderNotificationResponse from(OrderNotification n) {
        return new OrderNotificationResponse(
            n.getNotificationId(),
            n.getOrderId(),
            n.getEventType(),
            n.getStatus(),
            n.getSymbol(),
            n.getSide(),
            n.getQuantity(),
            n.getLimitPrice(),
            n.getExecutedPrice(),
            n.getReason(),
            n.getOccurredAt()
        );
    }
}
