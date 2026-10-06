package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarketDataCache.
 * 
 * Tests cover:
 * - Cache operations: append, retrieve, clear
 * - History: per-symbol append, time-window trimming, newest always kept
 * - Thread safety: concurrent access (implicitly tested by design)
 * - Data integrity: quote preservation and updates
 * - Optional handling: missing quotes return empty
 * - Cache statistics: size and age tracking
 * 
 * Test data uses realistic market quotes.
 */
@DisplayName("MarketDataCache Tests")
class MarketDataCacheTest {
    
    private MarketDataCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new MarketDataCache();
    }
    
    @Test
    @DisplayName("Should store and retrieve quote")
    void testStoreAndRetrieveQuote() {
        // Arrange
        String symbol = "INFY.NS";
        QuotePayload quote = new QuotePayload(
            symbol,
            new BigDecimal("1850.50"),
            new BigDecimal("1850.25"),
            new BigDecimal("1850.75"),
            "INR",
            new BigDecimal("5.50"),
            new BigDecimal("0.30"),
            new BigDecimal("1845.00"),
            "open",
            false,
            Instant.now()
        );
        Instant eventTime = Instant.now();
        
        // Act
        cache.updateQuote(symbol, quote, eventTime);
        Optional<QuotePayload> retrieved = cache.getLatestQuote(symbol);
        
        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(symbol, retrieved.get().symbol());
        assertEquals(new BigDecimal("1850.50"), retrieved.get().price());
    }
    
    @Test
    @DisplayName("Should return empty for non-existent symbol")
    void testGetNonExistentQuote() {
        // Act
        Optional<QuotePayload> quote = cache.getLatestQuote("NON_EXISTENT.NS");
        
        // Assert
        assertTrue(quote.isEmpty());
    }
    
    @Test
    @DisplayName("Should update existing quote")
    void testUpdateExistingQuote() {
        // Arrange
        String symbol = "INFY.NS";
        QuotePayload quote1 = new QuotePayload(
            symbol, new BigDecimal("1850.00"), new BigDecimal("1849.75"),
            new BigDecimal("1850.25"), "INR", new BigDecimal("5.00"),
            new BigDecimal("0.27"), new BigDecimal("1845.00"), "open", false, Instant.now()
        );
        
        QuotePayload quote2 = new QuotePayload(
            symbol, new BigDecimal("1855.00"), new BigDecimal("1854.75"),
            new BigDecimal("1855.25"), "INR", new BigDecimal("10.00"),
            new BigDecimal("0.54"), new BigDecimal("1845.00"), "open", false, Instant.now()
        );
        
        // Act
        cache.updateQuote(symbol, quote1, Instant.now());
        cache.updateQuote(symbol, quote2, Instant.now());
        
        // Assert
        assertEquals(
            new BigDecimal("1855.00"),
            cache.getLatestPrice(symbol).orElseThrow()
        );
    }
    
    @Test
    @DisplayName("Should retrieve latest price")
    void testGetLatestPrice() {
        // Arrange
        String symbol = "TCS.NS";
        BigDecimal price = new BigDecimal("3850.75");
        QuotePayload quote = new QuotePayload(
            symbol, price, new BigDecimal("3850.50"),
            new BigDecimal("3851.00"), "INR", new BigDecimal("15.75"),
            new BigDecimal("0.41"), new BigDecimal("3835.00"), "open", false, Instant.now()
        );
        
        // Act
        cache.updateQuote(symbol, quote, Instant.now());
        Optional<BigDecimal> retrievedPrice = cache.getLatestPrice(symbol);
        
        // Assert
        assertTrue(retrievedPrice.isPresent());
        assertEquals(price, retrievedPrice.get());
    }
    
    @Test
    @DisplayName("Should track quote timestamp")
    void testGetQuoteTimestamp() {
        // Arrange
        String symbol = "HDFC.NS";
        Instant eventTime = Instant.now();
        QuotePayload quote = new QuotePayload(
            symbol, new BigDecimal("2750.50"), new BigDecimal("2750.25"),
            new BigDecimal("2750.75"), "INR", new BigDecimal("0.50"),
            new BigDecimal("0.02"), new BigDecimal("2750.00"), "open", false, Instant.now()
        );
        
        // Act
        cache.updateQuote(symbol, quote, eventTime);
        Optional<Instant> timestamp = cache.getQuoteTimestamp(symbol);
        
        // Assert
        assertTrue(timestamp.isPresent());
        assertEquals(eventTime, timestamp.get());
    }
    
    @Test
    @DisplayName("Should check quote existence")
    void testHasQuote() {
        // Arrange
        String symbol = "RELIANCE.NS";
        QuotePayload quote = new QuotePayload(
            symbol, new BigDecimal("2750.50"), new BigDecimal("2750.25"),
            new BigDecimal("2750.75"), "INR", new BigDecimal("10.50"),
            new BigDecimal("0.38"), new BigDecimal("2740.00"), "open", false, Instant.now()
        );
        
        // Act
        assertFalse(cache.hasQuote(symbol));
        cache.updateQuote(symbol, quote, Instant.now());
        
        // Assert
        assertTrue(cache.hasQuote(symbol));
    }
    
    @Test
    @DisplayName("Should return cache size")
    void testGetCacheSize() {
        // Arrange
        String symbol1 = "INFY.NS";
        String symbol2 = "TCS.NS";
        String symbol3 = "HDFC.NS";
        
        QuotePayload quote = new QuotePayload(
            "", new BigDecimal("1000.00"), new BigDecimal("999.50"),
            new BigDecimal("1000.50"), "INR", new BigDecimal("0"),
            new BigDecimal("0"), null, "open", false, Instant.now()
        );
        
        // Act
        cache.updateQuote(symbol1, quote, Instant.now());
        assertEquals(1, cache.getCacheSize());
        
        cache.updateQuote(symbol2, quote, Instant.now());
        assertEquals(2, cache.getCacheSize());
        
        cache.updateQuote(symbol3, quote, Instant.now());
        assertEquals(3, cache.getCacheSize());
    }
    
    @Test
    @DisplayName("Should clear all quotes")
    void testClearCache() {
        // Arrange
        QuotePayload quote = new QuotePayload(
            "", new BigDecimal("1000.00"), new BigDecimal("999.50"),
            new BigDecimal("1000.50"), "INR", new BigDecimal("0"),
            new BigDecimal("0"), null, "open", false, Instant.now()
        );
        
        cache.updateQuote("INFY.NS", quote, Instant.now());
        cache.updateQuote("TCS.NS", quote, Instant.now());
        assertEquals(2, cache.getCacheSize());
        
        // Act
        cache.clearCache();
        
        // Assert
        assertEquals(0, cache.getCacheSize());
        assertTrue(cache.getLatestQuote("INFY.NS").isEmpty());
    }
    
    @Test
    @DisplayName("Should get all quotes")
    void testGetAllQuotes() {
        // Arrange
        QuotePayload quote1 = new QuotePayload(
            "INFY.NS", new BigDecimal("1850.50"), new BigDecimal("1850.25"),
            new BigDecimal("1850.75"), "INR", new BigDecimal("5.50"),
            new BigDecimal("0.30"), new BigDecimal("1845.00"), "open", false, Instant.now()
        );
        
        QuotePayload quote2 = new QuotePayload(
            "TCS.NS", new BigDecimal("3850.75"), new BigDecimal("3850.50"),
            new BigDecimal("3851.00"), "INR", new BigDecimal("15.75"),
            new BigDecimal("0.41"), new BigDecimal("3835.00"), "open", false, Instant.now()
        );
        
        // Act
        cache.updateQuote("INFY.NS", quote1, Instant.now());
        cache.updateQuote("TCS.NS", quote2, Instant.now());
        var allQuotes = cache.getAllQuotes();
        
        // Assert
        assertEquals(2, allQuotes.size());
        assertTrue(allQuotes.containsKey("INFY.NS"));
        assertTrue(allQuotes.containsKey("TCS.NS"));
        assertEquals(new BigDecimal("1850.50"), allQuotes.get("INFY.NS").price());
    }
    
    @Test
    @DisplayName("Should throw on null symbol")
    void testUpdateWithNullSymbol() {
        // Arrange
        QuotePayload quote = new QuotePayload(
            "TEST", new BigDecimal("1000.00"), new BigDecimal("999.50"),
            new BigDecimal("1000.50"), "INR", new BigDecimal("0"),
            new BigDecimal("0"), null, "open", false, Instant.now()
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> cache.updateQuote(null, quote, Instant.now())
        );
    }
    
    @Test
    @DisplayName("Should throw on null quote")
    void testUpdateWithNullQuote() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> cache.updateQuote("INFY.NS", null, Instant.now())
        );
    }
    
    @Test
    @DisplayName("Should handle blank symbol safely")
    void testHandleBlankSymbol() {
        // Act
        assertTrue(cache.getLatestQuote("").isEmpty());
        assertTrue(cache.getLatestQuote("   ").isEmpty());
        assertFalse(cache.hasQuote(""));
    }
    
    @Test
    @DisplayName("Should get cache statistics")
    void testGetStats() {
        // Arrange
        QuotePayload quote = new QuotePayload(
            "", new BigDecimal("1000.00"), new BigDecimal("999.50"),
            new BigDecimal("1000.50"), "INR", new BigDecimal("0"),
            new BigDecimal("0"), null, "open", false, Instant.now()
        );
        
        cache.updateQuote("INFY.NS", quote, Instant.now());
        cache.updateQuote("TCS.NS", quote, Instant.now());
        cache.updateQuote("HDFC.NS", quote, Instant.now());
        
        // Act
        MarketDataCache.CacheStats stats = cache.getStats();
        
        // Assert
        assertEquals(3, stats.totalSymbols);
        assertTrue(stats.oldestQuoteAgeMsec >= 0);
    }
    
    @Test
    @DisplayName("Should preserve quote metadata")
    void testPreserveQuoteMetadata() {
        // Arrange
        String symbol = "RELIANCE.NS";
        Instant quoteAsOf = Instant.parse("2026-10-04T10:30:00Z");
        QuotePayload quote = new QuotePayload(
            symbol,
            new BigDecimal("2750.50"),
            new BigDecimal("2750.25"),
            new BigDecimal("2750.75"),
            "INR",
            new BigDecimal("10.50"),
            new BigDecimal("0.38"),
            new BigDecimal("2740.00"),
            "open",
            true,  // stale
            quoteAsOf
        );
        
        // Act
        cache.updateQuote(symbol, quote, Instant.now());
        QuotePayload retrieved = cache.getLatestQuote(symbol).orElseThrow();
        
        // Assert
        assertEquals(symbol, retrieved.symbol());
        assertEquals(new BigDecimal("2750.50"), retrieved.price());
        assertEquals(new BigDecimal("2750.25"), retrieved.bid());
        assertEquals(new BigDecimal("2750.75"), retrieved.ask());
        assertEquals("open", retrieved.marketState());
        assertTrue(retrieved.stale());
        assertEquals(quoteAsOf, retrieved.quoteAsOf());
    }
    
    @Test
    @DisplayName("Should append quotes instead of replacing them")
    void testUpdateAppendsToHistory() {
        // Arrange
        Instant t1 = Instant.now().minusSeconds(600);
        Instant t2 = Instant.now().minusSeconds(300);
        
        // Act
        cache.updateQuote("AAPL", quote("AAPL", "190.00"), t1);
        cache.updateQuote("AAPL", quote("AAPL", "191.00"), t2);
        
        // Assert
        List<MarketDataCache.CachedQuote> history = cache.getQuoteHistory("AAPL");
        assertEquals(2, history.size());
        assertEquals(new BigDecimal("190.00"), history.get(0).quote().price());
        assertEquals(t1, history.get(0).receivedAt());
        assertEquals(new BigDecimal("191.00"), history.get(1).quote().price());
        assertEquals(new BigDecimal("191.00"), cache.getLatestPrice("AAPL").orElseThrow());
        assertEquals(t2, cache.getQuoteTimestamp("AAPL").orElseThrow());
        assertEquals(1, cache.getCacheSize());
        assertEquals(2, cache.getTotalQuoteCount());
        assertEquals(new BigDecimal("191.00"), cache.getAllQuotes().get("AAPL").price());
    }
    
    @Test
    @DisplayName("Should trim quotes older than the history window on write")
    void testTrimsOutsideWindow() {
        // Arrange
        MutableClock clock = new MutableClock(Instant.parse("2026-10-06T00:00:00Z"));
        MarketDataCache windowed = new MarketDataCache(Duration.ofHours(1), clock);
        windowed.updateQuote("AAPL", quote("AAPL", "190.00"), clock.instant());
        clock.advance(Duration.ofMinutes(30));
        windowed.updateQuote("AAPL", quote("AAPL", "191.00"), clock.instant());
        
        // Act: 61 minutes after the first quote
        clock.advance(Duration.ofMinutes(31));
        windowed.updateQuote("AAPL", quote("AAPL", "192.00"), clock.instant());
        
        // Assert
        assertEquals(List.of(new BigDecimal("191.00"), new BigDecimal("192.00")),
            windowed.getQuoteHistory("AAPL").stream().map(entry -> entry.quote().price()).toList());
    }
    
    @Test
    @DisplayName("Should always keep the newest quote, even past the window")
    void testKeepsNewestPastWindow() {
        // Arrange
        MutableClock clock = new MutableClock(Instant.parse("2026-10-06T00:00:00Z"));
        MarketDataCache windowed = new MarketDataCache(Duration.ofHours(1), clock);
        windowed.updateQuote("AAPL", quote("AAPL", "190.00"), clock.instant());
        windowed.updateQuote("AAPL", quote("AAPL", "191.00"), clock.instant().plusSeconds(60));
        
        // Act
        clock.advance(Duration.ofHours(5));
        
        // Assert: history read hides the expired entry, latest stays available
        List<MarketDataCache.CachedQuote> history = windowed.getQuoteHistory("AAPL");
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("191.00"), history.get(0).quote().price());
        assertEquals(new BigDecimal("191.00"), windowed.getLatestPrice("AAPL").orElseThrow());
        
        // A replayed quote older than the window is trimmed straight away, except as newest
        windowed.updateQuote("AAPL", quote("AAPL", "189.00"), Instant.parse("2026-10-05T00:00:00Z"));
        assertEquals(1, windowed.getQuoteHistory("AAPL").size());
    }
    
    @Test
    @DisplayName("Should use now when the event time is missing")
    void testNullEventTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-06T00:00:00Z"));
        MarketDataCache windowed = new MarketDataCache(Duration.ofHours(1), clock);
        
        windowed.updateQuote("AAPL", quote("AAPL", "190.00"), null);
        
        assertEquals(clock.instant(), windowed.getQuoteTimestamp("AAPL").orElseThrow());
    }
    
    @Test
    @DisplayName("Should return empty history for unknown or blank symbols")
    void testEmptyHistory() {
        assertTrue(cache.getQuoteHistory("UNKNOWN").isEmpty());
        assertTrue(cache.getQuoteHistory(null).isEmpty());
        assertTrue(cache.getQuoteHistory(" ").isEmpty());
    }
    
    @Test
    @DisplayName("Should reject a non-positive history window")
    void testRejectsInvalidWindow() {
        assertThrows(IllegalArgumentException.class, () -> new MarketDataCache(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MarketDataCache(Duration.ofMinutes(-5)));
        assertThrows(IllegalArgumentException.class, () -> new MarketDataCache((Duration) null));
    }
    
    @Test
    @DisplayName("Should return quotes received in a time range, oldest first, optionally for one symbol")
    void testQuotesReceivedBetween() {
        // Arrange
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        cache.updateQuote("AAPL", quote("AAPL", "189.00"), from.minusSeconds(1));    // before range
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1020.00"), from.plusSeconds(60));
        cache.updateQuote("AAPL", quote("AAPL", "190.00"), from);                    // inclusive start
        cache.updateQuote("AAPL", quote("AAPL", "191.00"), to);                      // exclusive end
        
        // Act
        List<MarketDataCache.CachedQuote> all = cache.getQuotesReceivedBetween(null, from, to);
        List<MarketDataCache.CachedQuote> aapl = cache.getQuotesReceivedBetween("AAPL", from, to);
        
        // Assert
        assertEquals(List.of(new BigDecimal("190.00"), new BigDecimal("1020.00")),
            all.stream().map(entry -> entry.quote().price()).toList());
        assertEquals(List.of(new BigDecimal("190.00")),
            aapl.stream().map(entry -> entry.quote().price()).toList());
        assertTrue(cache.getQuotesReceivedBetween("MSFT", from, to).isEmpty());
    }
    
    private static QuotePayload quote(String symbol, String price) {
        BigDecimal p = new BigDecimal(price);
        return new QuotePayload(symbol, p, p, p, "USD", null, null, null, "open", false, Instant.now());
    }
    
    /** Clock the tests can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now;
        
        MutableClock(Instant start) {
            this.now = start;
        }
        
        void advance(Duration duration) {
            now = now.plus(duration);
        }
        
        @Override
        public Instant instant() {
            return now;
        }
        
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
