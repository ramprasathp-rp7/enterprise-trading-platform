package com.tradingplatform.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tradingplatform.enums.OrderStatus;

/**
 * A FILLED or REJECTED order whose ORDER_FILLED / ORDER_REJECTED has not been acknowledged by Kafka
 * (orders.event_pending). Carries everything the trade event needs, so OutcomeReplayScheduler can
 * rebuild the event from the order row alone.
 *
 * @param price          the order's limit price, published as the quoted price
 * @param fee            the order's fee, published as the original fee
 * @param rejectedReason the rejection reason (REJECTED only)
 * @param fillFxRate     instrument-to-account FX rate at fill (FILLED only)
 */
public record PendingOutcome(
    UUID id,
    long accountId,
    OrderStatus status,
    BigDecimal price,
    BigDecimal executedPrice,
    BigDecimal fee,
    String rejectedReason,
    Instant executedOn,
    BigDecimal fillFxRate
) {
}
