package com.tradingplatform.api.analytics.model;

import java.time.Instant;

/**
 * Dimension table: one row per instrument (Type 1, overwrite in place).
 */
public record AnalyticsDimInstrument(
    Long instrumentKey,
    String symbol,
    String name,
    String assetClass,
    String currency,
    String exchange,
    Boolean tradable,
    Instant loadedAt
) {}
