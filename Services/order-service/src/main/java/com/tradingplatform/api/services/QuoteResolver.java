package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves quotes for a set of symbols, spending Fauxnance quota only when the cache cannot answer.
 * Shared by portfolio valuation and FX rate lookups so a valuation prices its instruments and the FX
 * pairs it needs in the same request.
 *
 * Order:
 * 1. The market-data cache, for symbols whose newest quote was received within
 *    market-data.quote-resolver.max-age (default PT10M, two refresh intervals). The cache is kept
 *    current by MarketDataRefreshScheduler or MarketDataConsumer, so this is the normal path.
 * 2. Fauxnance (one batched call) for symbols that are missing from the cache or older than that.
 * 3. An older cached quote, marked stale, for anything Fauxnance did not return.
 */
@Service
public class QuoteResolver {

    private static final Logger log = LoggerFactory.getLogger(QuoteResolver.class);

    /** A resolved quote; {@code stale} is true for flagged stale quotes and for cached quotes past max age. */
    public record ResolvedQuote(FauxnanceApiClient.QuoteData quote, boolean stale) {}

    private final FauxnanceApiClient fauxnanceClient;
    private final MarketDataCache marketDataCache;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public QuoteResolver(FauxnanceApiClient fauxnanceClient, MarketDataCache marketDataCache,
                         @Value("${market-data.quote-resolver.max-age:PT10M}") Duration maxAge) {
        this(fauxnanceClient, marketDataCache, maxAge, Clock.systemUTC());
    }

    QuoteResolver(FauxnanceApiClient fauxnanceClient, MarketDataCache marketDataCache, Duration maxAge, Clock clock) {
        this.fauxnanceClient = Objects.requireNonNull(fauxnanceClient, "fauxnanceClient cannot be null");
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
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
        Instant cutoff = clock.instant().minus(maxAge);

        // Cache first: recent quotes need no Fauxnance call
        List<String> needFauxnance = new ArrayList<>();
        for (String symbol : requested) {
            Optional<QuotePayload> cached = marketDataCache.getLatestQuote(symbol);
            Optional<Instant> receivedAt = marketDataCache.getQuoteTimestamp(symbol);
            if (cached.isPresent() && receivedAt.isPresent() && !receivedAt.get().isBefore(cutoff)) {
                QuotePayload quote = cached.get();
                result.put(symbol, new ResolvedQuote(toQuoteData(quote, receivedAt.get()), Boolean.TRUE.equals(quote.stale())));
            } else {
                needFauxnance.add(symbol);
            }
        }

        if (needFauxnance.isEmpty()) {
            log.debug("Resolved all {} quotes from the cache", requested.size());
            return result;
        }

        // Fauxnance only for symbols the cache could not answer
        log.info("Cache missing or old for {} of {} symbols, fetching from Fauxnance: {}",
            needFauxnance.size(), requested.size(), needFauxnance);
        try {
            Map<String, FauxnanceApiClient.QuoteData> fauxnanceQuotes = fauxnanceClient.getBatchedQuotes(needFauxnance);
            for (FauxnanceApiClient.QuoteData quote : fauxnanceQuotes.values()) {
                result.put(quote.getSymbol(), new ResolvedQuote(quote, quote.isStale()));
            }
        } catch (Exception ex) {
            log.warn("Fauxnance API failed, falling back to older cached quotes: {}", ex.getMessage());
        }

        // Last resort: an older cached quote, marked stale
        for (String symbol : needFauxnance) {
            if (result.containsKey(symbol)) {
                continue;
            }
            Optional<QuotePayload> cached = marketDataCache.getLatestQuote(symbol);
            if (cached.isPresent()) {
                Instant receivedAt = marketDataCache.getQuoteTimestamp(symbol).orElse(clock.instant());
                result.put(symbol, new ResolvedQuote(toQuoteData(cached.get(), receivedAt), true));
                log.debug("Using older cached quote for {}: {}", symbol, cached.get().price());
            } else {
                log.warn("No quote available for symbol {}", symbol);
            }
        }

        log.info("Resolved {} of {} quotes", result.size(), requested.size());
        return result;
    }

    /**
     * Cached quote as QuoteData. Kafka quotes may lack bid, ask or observation time, which QuoteData
     * requires, so those fall back to the price and the time the cache received the quote.
     */
    private static FauxnanceApiClient.QuoteData toQuoteData(QuotePayload quote, Instant receivedAt) {
        return new FauxnanceApiClient.QuoteData(
            quote.symbol(),
            quote.price(),
            quote.bid() != null ? quote.bid() : quote.price(),
            quote.ask() != null ? quote.ask() : quote.price(),
            quote.currency(),
            quote.quoteAsOf() != null ? quote.quoteAsOf() : receivedAt,
            Boolean.TRUE.equals(quote.stale()),
            quote.change(),
            quote.changePercent(),
            quote.previousClose(),
            quote.marketState()
        );
    }
}
