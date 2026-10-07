package com.tradingplatform.strategy.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.services.MarketDataCache;

/**
 * One distinct price observation of a symbol.
 *
 * @param at    observation time (the quote's quoteAsOf, or when it was received if that is missing)
 * @param price last price (midpoint)
 * @param bid   sell side, used to price a SELL
 * @param ask   buy side, used to price a BUY
 */
public record PricePoint(Instant at, BigDecimal price, BigDecimal bid, BigDecimal ask) {

    /**
     * The cache's quote history as a series of distinct observations, oldest first.
     * The quote cache refreshes on a timer even when the market is closed, repeating the same
     * observation; counting those repeats would move the averages without the price moving, so
     * quotes are collapsed by observation time (the newest copy wins). Quotes without a price are dropped.
     */
    public static List<PricePoint> series(List<MarketDataCache.CachedQuote> history) {
        Map<Instant, PricePoint> byTime = new LinkedHashMap<>();
        for (MarketDataCache.CachedQuote cached : history) {
            QuotePayload q = cached.quote();
            if (q == null || q.price() == null) {
                continue;
            }
            Instant at = q.quoteAsOf() != null ? q.quoteAsOf() : cached.receivedAt();
            byTime.put(at, new PricePoint(at, q.price(), q.bid(), q.ask()));
        }
        List<PricePoint> points = new ArrayList<>(byTime.values());
        points.sort(Comparator.comparing(PricePoint::at));
        return points;
    }

    /** The price an order for this signal would pay: ask to buy, bid to sell, last price if the side is missing */
    public BigDecimal executionPrice(boolean buy) {
        BigDecimal side = buy ? ask : bid;
        return side != null && side.signum() > 0 ? side : price;
    }
}
