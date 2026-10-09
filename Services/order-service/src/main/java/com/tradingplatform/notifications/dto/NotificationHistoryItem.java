package com.tradingplatform.notifications.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One entry of GET /api/v1/accounts/{accountId}/notification-history (contract:
 * order-notifications-api.yaml). Exactly one of {@code order} and {@code priceAlert} is set,
 * matching {@code kind}.
 *
 * @param id       stable across calls: "order-{notificationId}" or "alert-{triggerId}"
 * @param status   FILLED, REJECTED or CANCELLED for an order; DELIVERED, PENDING or FAILED for a price alert
 * @param channels the channels it went out on, e.g. [EMAIL, IN_APP]
 * @param inApp    whether it was (also) shown in the bell
 */
public record NotificationHistoryItem(
        String id,
        Kind kind,
        Instant occurredAt,
        String status,
        String symbol,
        List<String> channels,
        boolean inApp,
        OrderDetails order,
        PriceAlertDetails priceAlert) {

    public enum Kind { ORDER, PRICE_ALERT }

    public record OrderDetails(String orderId, String eventType, String side, Integer quantity,
                               BigDecimal limitPrice, BigDecimal executedPrice, String reason) {
    }

    public record PriceAlertDetails(Long alertId, Long triggerId, String direction, BigDecimal threshold,
                                    String mode, BigDecimal price, Instant quoteAt, String reason) {
    }
}
