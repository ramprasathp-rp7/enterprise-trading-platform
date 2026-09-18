package com.tradingplatform.api.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fact table: one row per order, in whatever status it reached.
 */
public record AnalyticsFactTrade(
    Long tradeKey,
    Long accountKey,
    Long instrumentKey,
    Integer dateKey,
    String side,
    Integer quantity,
    BigDecimal price,
    String status,
    BigDecimal executedPrice,
    BigDecimal tradeValue,
    String sourceOrderId,
    Instant createdAt,
    Instant loadedAt
) {}
