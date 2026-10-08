package com.tradingplatform.domain.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Cached quotes received on one UTC day, oldest first.
 *
 * @param date The UTC day the quotes were received on
 * @param symbol The symbol filter, or null for all symbols
 * @param count Number of rows
 * @param quotes The rows, ordered by reception time then symbol
 */
public record CachedQuotesDayResponse(
    LocalDate date,
    String symbol,
    int count,
    List<CachedQuoteResponse> quotes
) {
}
