package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for market data quotes.
 * 
 * This service provides in-memory storage and retrieval of the latest market quotes
 * from the Kafka market-data topic. It's designed for high-volume quote updates with
 * concurrent read and write operations.
 * 
 * Thread Safety:
 * - Uses ConcurrentHashMap for thread-safe concurrent access
 * - Per-symbol locking not required (ConcurrentHashMap handles fine-grained locking)
 * - Suitable for high-frequency quote updates (thousands per second)
 * 
 * Data Model:
 * - Stores only the latest quote per symbol (overwrites older data)
 * - Maintains event timestamp for freshness tracking
 * - Preserves full quote metadata (bid, ask, spread, market state, staleness flag)
 * 
 * Usage:
 * - MarketDataConsumer updates quotes via updateQuote()
 * - Position valuation services query quotes via getLatestQuote()
 * - Analytics services scan all symbols via getAllQuotes()
 * - Monitoring services check freshness via getQuoteTimestamp()
 * 
 * Limitations:
 * - No persistence: quotes lost on service restart
 * - No historical data: only latest quote per symbol maintained
 * - No automatic staleness eviction: stale flag depends on Fauxnance
 * - Unbounded growth: new symbols are added without limit
 * 
 * Future Enhancements:
 * - Add time-based eviction for inactive symbols (LRU cache)
 * - Add metrics for cache hit/miss rates
 * - Add persistence layer (Redis, database)
 * - Add batch notification for quote updates
 * 
 * Related:
 * @see MarketDataConsumer for quote consumption
 * @see QuotePayload for quote data structure
 */
@Service
public class MarketDataCache {
    
    private static final Logger logger = LoggerFactory.getLogger(MarketDataCache.class);
    
    /**
     * Main cache: symbol -> (QuoteData: quote + timestamp)
     * Using ConcurrentHashMap for thread-safe operations without explicit synchronization.
     * Supports concurrent reads while writes are happening.
     */
    private final Map<String, QuoteData> quoteCache = new ConcurrentHashMap<>();
    
    /**
     * Internal data class holding a quote with its reception timestamp.
     * Enables tracking when each quote was received for freshness analysis.
     */
    private static class QuoteData {
        final QuotePayload quote;
        final Instant receivedAt;
        
        QuoteData(QuotePayload quote, Instant receivedAt) {
            this.quote = quote;
            this.receivedAt = receivedAt;
        }
    }
    
    /**
     * Updates the cache with a new quote for the given symbol.
     * Overwrites any previous quote for the symbol.
     * This is called by MarketDataConsumer for each QUOTE event.
     * 
     * @param symbol The instrument symbol (key)
     * @param quote The quote payload with price data
     * @param eventTime The timestamp when the quote was received
     * @throws IllegalArgumentException if symbol or quote is null
     */
    public void updateQuote(String symbol, QuotePayload quote, Instant eventTime) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (quote == null) {
            throw new IllegalArgumentException("Quote cannot be null");
        }
        
        QuoteData quoteData = new QuoteData(quote, eventTime);
        quoteCache.put(symbol, quoteData);
        
        logger.debug(
            "Quote cached: symbol={}, price={}, receivedAt={}, cacheSize={}",
            symbol, quote.price(), eventTime, quoteCache.size()
        );
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
        
        QuoteData data = quoteCache.get(symbol);
        if (data != null) {
            logger.debug("Quote cache hit: symbol={}, price={}", symbol, data.quote.price());
            return Optional.of(data.quote);
        } else {
            logger.debug("Quote cache miss: symbol={}", symbol);
            return Optional.empty();
        }
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
        
        QuoteData data = quoteCache.get(symbol);
        return data != null ? Optional.of(data.receivedAt) : Optional.empty();
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
     * Retrieves a snapshot of all cached quotes.
     * Returns a copy to prevent external modification of cache state.
     * 
     * @return Map of symbol -> QuotePayload for all cached quotes
     */
    public Map<String, QuotePayload> getAllQuotes() {
        Map<String, QuotePayload> snapshot = new ConcurrentHashMap<>();
        quoteCache.forEach((symbol, quoteData) -> 
            snapshot.put(symbol, quoteData.quote)
        );
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
        logger.warn("Market data cache cleared. Removed {} quotes", size);
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
