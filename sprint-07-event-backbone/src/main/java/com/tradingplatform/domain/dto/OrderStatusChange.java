package com.tradingplatform.domain.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingplatform.domain.enums.OrderStatus;

/**
 * Represents one immutable status transition entry for an order.
 * Backed by the order_history table that is populated by trigger.
 */
public record OrderStatusChange(
    String orderId,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant timestamp,
    OrderStatus fromStatus,
    OrderStatus toStatus
) {
}
