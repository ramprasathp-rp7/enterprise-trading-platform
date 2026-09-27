package com.tradingplatform.domain.dto;

import java.time.Instant;

import com.tradingplatform.domain.enums.OrderStatus;

/**
 * Represents one immutable status transition entry for an order.
 * Backed by the order_history table that is populated by trigger.
 */
public record OrderStatusChange(
    String orderId,
    Instant timestamp,
    OrderStatus fromStatus,
    OrderStatus toStatus
) {
}
