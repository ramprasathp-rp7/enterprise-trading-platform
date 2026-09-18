package com.tradingplatform.api.analytics.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dimension table: one row per account version (Type 2, slowly changing).
 */
public record AnalyticsDimAccount(
    Long accountKey,
    String accountId,
    String holderName,
    String status,
    LocalDate effectiveDate,
    LocalDate endDate,
    Boolean isCurrent,
    Long sourceId,
    Instant loadedAt
) {}
