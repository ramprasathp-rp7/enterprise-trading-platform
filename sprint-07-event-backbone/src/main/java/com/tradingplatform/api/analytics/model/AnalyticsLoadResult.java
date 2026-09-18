package com.tradingplatform.api.analytics.model;

import java.time.Instant;

/**
 * Result of a complete analytics load run.
 */
public record AnalyticsLoadResult(
    Instant startTime,
    Instant endTime,
    Integer dimDateRowsLoaded,
    Integer dimInstrumentRowsLoaded,
    Integer dimAccountRowsLoaded,
    Integer factTradesRowsLoaded,
    Integer deadLetteredCount,
    String batchId
) {}
