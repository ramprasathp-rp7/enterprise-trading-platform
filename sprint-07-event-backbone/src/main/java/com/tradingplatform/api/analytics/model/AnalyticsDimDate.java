package com.tradingplatform.api.analytics.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dimension table: one row per calendar day.
 */
public record AnalyticsDimDate(
    Integer dateKey,
    LocalDate fullDate,
    Integer day,
    Integer month,
    Integer year,
    Integer quarter,
    Integer dayOfWeek,
    String dayName,
    String monthName,
    Boolean isWeekday,
    Instant loadedAt
) {}
