package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Thread-safe cache for market data quotes.
 *
 * This service provides in-memory storage and retrieval of market quotes, written either
 * by the scheduled Fauxnance refresh or by the Kafka market-data topic. It's designed for
 * high-volume quote updates with concurrent read and write operations.
 *
 * Thread Safety:
 * - Uses ConcurrentHashMap for thread-safe concurrent access
 * - Writes to a symbol are serialised through ConcurrentHashMap.compute
 * - Each symbol's history is a ConcurrentLinkedDeque, so reads never block writes
 *
 * Data Model:
 * - Appends every quote to a per-symbol history, oldest first (nothing is overwritten)
 * - Keeps quotes received within the history window (market-data.cache.history-window,
 *   default 24 hours); older ones are trimmed on each write
 * - The newest quote is always kept, even past the window, so a symbol never loses its last price
 * - Maintains reception timestamp for freshness tracking
 * - Preserves full quote metadata (bid, ask, spread, market state, staleness flag)
 *
 * Usage:
 * - One writer, picked by market-data.quote-source (see QuoteSource):
 *   MarketDataRefreshScheduler (FAUXNANCE) or MarketDataConsumer (KAFKA) via updateQuote()
 * - Position valuation services query the newest quote via getLatestQuote()
 * - Analytics services read a symbol's history via getQuoteHistory()
 *   and scan the newest quote for all symbols via getAllQuotes()
 * - Monitoring services check freshness via getQuoteTimestamp()
 *
 * Persistence:
 * - MarketDataCacheFileStore snapshots the cache to a JSON file and restores it on startup,
 *   so quotes survive a restart (see market-data.cache.file / wipe-on-startup)
 *
 * Limitations:
 * - Symbols are never evicted: a symbol keeps its newest quote until clearCache()
 *
 * Related:
 * @see MarketDataConsumer for quote consumption
 * @see MarketDataRefreshScheduler for the scheduled Fauxnance refresh
 * @see QuotePayload for quote data structure
 */
@Service
public class MarketDataCache {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataCache.class);

    /** History window used when none is configured. */
    public static final Duration DEFAULT_HISTORY_WINDOW = Duration.ofHours(24);

    /**
     * Main cache: symbol -> history of (quote + reception timestamp), oldest first.
     * Using ConcurrentHashMap for thread-safe operations without explicit synchronization.
     * Supports concurrent reads while writes are happening.
     */
    private final Map<String, Deque<CachedQuote>> quoteCache = new ConcurrentHashMap<>();

    /** Bumped on every write or clear, so the file store can skip saving an unchanged cache. */
    private final AtomicLong version = new AtomicLong();

    private final Duration historyWindow;
    private final Clock clock;

    /**
     * A cached quote with the time it was received.
     * Enables tracking when each quote was received for freshness analysis.
     *
     * @param quote The quote payload
     * @param receivedAt When the quote was received (event time or refresh time)
     */
    public record CachedQuote(QuotePayload quote, Instant receivedAt) {}

    /** Cache with the default 24-hour history window. */
    public MarketDataCache() {
        this(DEFAULT_HISTORY_WINDOW, Clock.systemUTC());
    }

    /**
     * @param historyWindow How long quotes are kept per symbol (ISO-8601, e.g. PT24H)
     */
    @Autowired
    public MarketDataCache(@Value("${market-data.cache.history-window:PT24H}") Duration historyWindow) {
        this(historyWindow, Clock.systemUTC());
    }

    MarketDataCache(Duration historyWindow, Clock clock) {
        if (historyWindow == null || historyWindow.isZero() || historyWindow.isNegative()) {
            throw new IllegalArgumentException("History window must be positive, got " + historyWindow);
        }
        this.historyWindow = historyWindow;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        logger.info("Market data cache history window: {}", historyWindow);
    }

    /**
     * Appends a new quote to the given symbol's history.
     * Earlier quotes are kept; those received before the history window are trimmed.
     * This is called by the active quote writer for each new quote.
     *
     * @param symbol The instrument symbol (key)
     * @param quote The quote payload with price data
     * @param eventTime The timestamp when the quote was received (now if null)
     * @throws IllegalArgumentException if symbol or quote is null
     */
    public void updateQuote(String symbol, QuotePayload quote, Instant eventTime) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (quote == null) {
            throw new IllegalArgumentException("Quote cannot be null");
        }

        CachedQuote cachedQuote = new CachedQuote(quote, eventTime != null ? eventTime : clock.instant());
        Deque<CachedQuote> history = quoteCache.compute(symbol, (key, existing) -> {
            Deque<CachedQuote> deque = existing != null ? existing : new ConcurrentLinkedDeque<>();
            deque.addLast(cachedQuote);
            trim(deque);
            return deque;
        });
        version.incrementAndGet();

        logger.debug(
            "Quote cached: symbol={}, price={}, receivedAt={}, historySize={}, cacheSize={}",
            symbol, quote.price(), cachedQuote.receivedAt(), history.size(), quoteCache.size()
        );
    }

    /**
     * Drops quotes received before the history window, always keeping the newest one.
     * Called while holding the symbol's compute lock.
     */
    private void trim(Deque<CachedQuote> history) {
        Instant cutoff = clock.instant().minus(historyWindow);
        while (history.size() > 1 && history.peekFirst().receivedAt().isBefore(cutoff)) {
            history.pollFirst();
        }
    }

    /** Newest entry for a symbol, or null. */
    private CachedQuote latest(String symbol) {
        Deque<CachedQuote> history = quoteCache.get(symbol);
        return history != null ? history.peekLast() : null;
    }

    /**
     * Retrieves the latest quote for a given symbol.
     * Returns empty if no quote has been received yet for this symbol.
     *
     * @param symbol The instrument symbol to query
     * @return Optional containing the latest QuotePayload, or empty if not found
     */
    public Optional<QuotePayload> getLatestQuote(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            logger.warn("getLatestQuote called with null/blank symbol");
            return Optional.empty();
        }

        CachedQuote data = latest(symbol);
        if (data != null) {
            logger.debug("Quote cache hit: symbol={}, price={}", symbol, data.quote().price());
            return Optional.of(data.quote());
        } else {
            logger.debug("Quote cache miss: symbol={}", symbol);
            return Optional.empty();
        }
    }

    /**
     * Retrieves the quotes held for a symbol within the history window, oldest first.
     * The newest quote is included even if it is older than the window.
     *
     * @param symbol The instrument symbol to query
     * @return Snapshot of the symbol's history (empty if none)
     */
    public List<CachedQuote> getQuoteHistory(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        Deque<CachedQuote> history = quoteCache.get(symbol);
        if (history == null) {
            return List.of();
        }

        CachedQuote newest = history.peekLast();
        Instant cutoff = clock.instant().minus(historyWindow);
        return history.stream()
            .filter(entry -> entry == newest || !entry.receivedAt().isBefore(cutoff))
            .toList();
    }

    /**
     * Retrieves every cached quote received in [from, to), oldest first.
     * Used for "rows of the day" style reads; only quotes still held by the cache are returned.
     *
     * @param symbol Restrict to this symbol, or null for all symbols
     * @param from Inclusive lower bound on reception time
     * @param to Exclusive upper bound on reception time
     * @return Matching quotes ordered by reception time, then symbol
     */
    public List<CachedQuote> getQuotesReceivedBetween(String symbol, Instant from, Instant to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");

        Stream<Deque<CachedQuote>> histories = symbol == null
            ? quoteCache.values().stream()
            : Stream.ofNullable(quoteCache.get(symbol));

        return histories
            .flatMap(Deque::stream)
            .filter(entry -> !entry.receivedAt().isBefore(from) && entry.receivedAt().isBefore(to))
            .sorted(Comparator.comparing(CachedQuote::receivedAt)
                .thenComparing(entry -> entry.quote().symbol()))
            .toList();
    }

    /**
     * Retrieves the latest price for a given symbol.
     * Convenience method for position valuation and order execution.
     *
     * @param symbol The instrument symbol to query
     * @return Optional containing the last observed price, or empty if not found
     */
    public Optional<BigDecimal> getLatestPrice(String symbol) {
        return getLatestQuote(symbol).map(QuotePayload::price);
    }

    /**
     * Retrieves the timestamp when the latest quote was received.
     * Used for freshness tracking and monitoring.
     *
     * @param symbol The instrument symbol to query
     * @return Optional containing the reception timestamp, or empty if not found
     */
    public Optional<Instant> getQuoteTimestamp(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        CachedQuote data = latest(symbol);
        return data != null ? Optional.of(data.receivedAt()) : Optional.empty();
    }

    /**
     * Checks if a quote has been received for the given symbol.
     *
     * @param symbol The instrument symbol to check
     * @return true if quote exists, false otherwise
     */
    public boolean hasQuote(String symbol) {
        return symbol != null && quoteCache.containsKey(symbol);
    }

    /**
     * Returns the number of symbols currently in the cache.
     * Useful for monitoring and health checks.
     *
     * @return count of unique symbols in cache
     */
    public int getCacheSize() {
        return quoteCache.size();
    }

    /**
     * Returns the number of quotes held across all symbols' histories.
     *
     * @return total cached quotes
     */
    public int getTotalQuoteCount() {
        return quoteCache.values().stream().mapToInt(Deque::size).sum();
    }

    /**
     * Retrieves a snapshot of the latest quote for every cached symbol.
     * Returns a copy to prevent external modification of cache state.
     *
     * @return Map of symbol -> latest QuotePayload for all cached symbols
     */
    public Map<String, QuotePayload> getAllQuotes() {
        Map<String, QuotePayload> snapshot = new ConcurrentHashMap<>();
        quoteCache.forEach((symbol, history) -> {
            CachedQuote newest = history.peekLast();
            if (newest != null) {
                snapshot.put(symbol, newest.quote());
            }
        });
        return snapshot;
    }

    /**
     * Clears all cached quotes.
     * Useful for testing and cache reset scenarios.
     * WARNING: This removes all quotes and should only be used in testing!
     */
    public void clearCache() {
        int size = quoteCache.size();
        quoteCache.clear();
        version.incrementAndGet();
        logger.warn("Market data cache cleared. Removed {} symbols", size);
    }

    /**
     * Copy of every symbol's history (oldest first), as held right now.
     * Used by MarketDataCacheFileStore to save the cache.
     *
     * @return symbol -> history snapshot
     */
    public Map<String, List<CachedQuote>> snapshot() {
        Map<String, List<CachedQuote>> copy = new ConcurrentHashMap<>();
        quoteCache.forEach((symbol, history) -> copy.put(symbol, List.copyOf(history)));
        return copy;
    }

    /**
     * Replays saved histories into the cache in reception order, trimming anything now outside
     * the history window. Call once at startup, before any live writer runs: a restored quote
     * appended after a live one would become the symbol's "newest".
     *
     * @param saved symbol -> history, as returned by snapshot()
     * @return number of quotes restored (before trimming)
     */
    public int restore(Map<String, List<CachedQuote>> saved) {
        List<Map.Entry<String, CachedQuote>> entries = saved.entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(q -> Map.entry(e.getKey(), q)))
            .filter(e -> e.getValue() != null && e.getValue().quote() != null && e.getValue().receivedAt() != null)
            .sorted(Comparator.comparing((Map.Entry<String, CachedQuote> e) -> e.getValue().receivedAt()))
            .toList();
        entries.forEach(e -> updateQuote(e.getKey(), e.getValue().quote(), e.getValue().receivedAt()));
        return entries.size();
    }

    /** Monotonic change counter; differs whenever the cache content may have changed. */
    public long version() {
        return version.get();
    }

    /**
     * Gets detailed cache statistics for monitoring.
     * Returns cache health information for observability.
     *
     * @return CacheStats object with current cache metrics
     */
    public CacheStats getStats() {
        return new CacheStats(
            quoteCache.size(),
            quoteCache.keySet().stream()
                .map(symbol -> getQuoteTimestamp(symbol)
                    .map(ts -> System.currentTimeMillis() - ts.toEpochMilli())
                    .orElse(Long.MAX_VALUE)
                )
                .min(Long::compare)
                .orElse(Long.MAX_VALUE)
        );
    }

    /**
     * Cache statistics for monitoring and alerting.
     */
    public static class CacheStats {
        public final int totalSymbols;
        public final long oldestQuoteAgeMsec;

        public CacheStats(int totalSymbols, long oldestQuoteAgeMsec) {
            this.totalSymbols = totalSymbols;
            this.oldestQuoteAgeMsec = oldestQuoteAgeMsec;
        }

        @Override
        public String toString() {
            return "CacheStats{" +
                "totalSymbols=" + totalSymbols +
                ", oldestQuoteAgeMsec=" + oldestQuoteAgeMsec +
                '}';
        }
    }
}
