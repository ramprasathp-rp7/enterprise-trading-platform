package com.tradingplatform.events;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for QUOTE events on the 'market-data' topic.
 * Represents real-time or near-real-time price quotes from Fauxnance.
 * High volume, low value per message. Keyed by symbol for per-symbol ordering.
 *
 * @param symbol Instrument symbol (also the message key).
 * @param price Last observed price (the last midpoint).
 * @param bid Sell side of the quote, below price (modelled by Fauxnance).
 * @param ask Buy side of the quote, above price (modelled by Fauxnance).
 * @param currency ISO 4217 currency code, 3 characters (e.g., "USD").
 * @param change Absolute change against previous close (nullable).
 * @param changePercent Percentage points change (nullable).
 * @param previousClose Previous closing price (nullable).
 * @param marketState Market state from Fauxnance: open, closed, pre, post, unknown.
 * @param stale True if quote is past its freshness window (indicates delayed data).
 * @param quoteAsOf Observation time from Fauxnance (distinct from eventTime).
 */
public record QuotePayload(
    @JsonProperty("symbol")
    String symbol,
    
    @JsonProperty("price")
    BigDecimal price,
    
    @JsonProperty("bid")
    BigDecimal bid,
    
    @JsonProperty("ask")
    BigDecimal ask,
    
    @JsonProperty("currency")
    String currency,
    
    @JsonProperty("change")
    BigDecimal change,
    
    @JsonProperty("changePercent")
    BigDecimal changePercent,
    
    @JsonProperty("previousClose")
    BigDecimal previousClose,
    
    @JsonProperty("marketState")
    String marketState,
    
    @JsonProperty("stale")
    Boolean stale,
    
    @JsonProperty("quoteAsOf")
    Instant quoteAsOf
) {}
