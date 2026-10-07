package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves quotes for a set of symbols: Fauxnance first (one batched call), then the market-data cache
 * for anything Fauxnance did not return. Shared by portfolio valuation and FX rate lookups so a
 * valuation prices its instruments and the FX pairs it needs in the same request.
 */
@Service
public class QuoteResolver {

    private static final Logger log = LoggerFactory.getLogger(QuoteResolver.class);

    /** A resolved quote; {@code stale} is true for Fauxnance-flagged stale quotes and every cache fallback. */
    public record ResolvedQuote(FauxnanceApiClient.QuoteData quote, boolean stale) {}

    private final FauxnanceApiClient fauxnanceClient;
    private final MarketDataCache marketDataCache;

    public QuoteResolver(FauxnanceApiClient fauxnanceClient, MarketDataCache marketDataCache) {
        this.fauxnanceClient = Objects.requireNonNull(fauxnanceClient, "fauxnanceClient cannot be null");
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
    }

    /**
     * @return quotes keyed by symbol; symbols with no quote anywhere are absent
     */
    public Map<String, ResolvedQuote> resolve(Collection<String> symbols) {
        Map<String, ResolvedQuote> result = new HashMap<>();
        if (symbols == null || symbols.isEmpty()) {
            return result;
        }
        List<String> requested = symbols.stream().distinct().toList();

        log.info("Resolving quotes for {} symbols: {}", requested.size(), requested);

        // Try Fauxnance first
        try {
            Map<String, FauxnanceApiClient.QuoteData> fauxnanceQuotes = fauxnanceClient.getBatchedQuotes(requested);
            log.info("Fauxnance returned {} quotes", fauxnanceQuotes.size());
            for (FauxnanceApiClient.QuoteData quote : fauxnanceQuotes.values()) {
                result.put(quote.getSymbol(), new ResolvedQuote(quote, quote.isStale()));
            }
        } catch (Exception ex) {
            log.warn("Fauxnance API failed, falling back to cache: {}", ex.getMessage());
        }

        // Fall back to cache for symbols not in Fauxnance result
        for (String symbol : requested) {
            if (!result.containsKey(symbol)) {
                Optional<QuotePayload> cachedQuote = marketDataCache.getLatestQuote(symbol);
                if (cachedQuote.isPresent()) {
                    QuotePayload quote = cachedQuote.get();
                    FauxnanceApiClient.QuoteData qd = new FauxnanceApiClient.QuoteData(
                            quote.symbol(),
                            quote.price(),
                            quote.bid(),
                            quote.ask(),
                            quote.currency(),
                            quote.quoteAsOf(),
                            quote.stale() != null && quote.stale()
                    );
                    result.put(symbol, new ResolvedQuote(qd, true));  // Mark as stale from cache
                    log.debug("Using cached quote for {}: {}", symbol, quote.price());
                } else {
                    log.warn("No quote available for symbol {}", symbol);
                }
            }
        }

        log.info("Resolved {} of {} quotes", result.size(), requested.size());
        return result;
    }
}
