package com.tradingplatform.domain.dto;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.services.MarketDataCache;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One cached quote, as returned by the market data endpoints.
 *
 * @param symbol Instrument symbol
 * @param price Last observed price (midpoint)
 * @param bid Sell side of the quote
 * @param ask Buy side of the quote
 * @param currency ISO 4217 currency code
 * @param change Absolute change against previous close (null for Fauxnance-refreshed quotes)
 * @param changePercent Percentage change (null for Fauxnance-refreshed quotes)
 * @param previousClose Previous closing price (null for Fauxnance-refreshed quotes)
 * @param marketState Market state (null for Fauxnance-refreshed quotes)
 * @param stale True if the quote was past its freshness window when received
 * @param quoteAsOf Observation time from Fauxnance
 * @param receivedAt When the cache received the quote
 */
public record CachedQuoteResponse(
    String symbol,
    BigDecimal price,
    BigDecimal bid,
    BigDecimal ask,
    String currency,
    BigDecimal change,
    BigDecimal changePercent,
    BigDecimal previousClose,
    String marketState,
    Boolean stale,
    Instant quoteAsOf,
    Instant receivedAt
) {
    public static CachedQuoteResponse from(MarketDataCache.CachedQuote cached) {
        QuotePayload quote = cached.quote();
        return new CachedQuoteResponse(
            quote.symbol(),
            quote.price(),
            quote.bid(),
            quote.ask(),
            quote.currency(),
            quote.change(),
            quote.changePercent(),
            quote.previousClose(),
            quote.marketState(),
            quote.stale(),
            quote.quoteAsOf(),
            cached.receivedAt()
        );
    }
}
