package com.tradingplatform.domain.dto;

import java.time.LocalDate;

/**
 * Optional JSON body for GET /api/v1/market-data/quotes, e.g. {"symbol": "INFY.NS", "date": "2026-10-06"}.
 * Query parameters of the same name take precedence.
 *
 * @param symbol Restrict to one instrument (null for all symbols)
 * @param date The UTC day (null for today)
 */
public record QuoteDayRequest(
    String symbol,
    LocalDate date
) {
}
