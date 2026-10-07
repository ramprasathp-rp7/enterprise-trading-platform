package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("QuoteResolver Tests")
class QuoteResolverTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private FauxnanceApiClient fauxnance;
    private MarketDataCache cache;
    private QuoteResolver resolver;

    @BeforeEach
    void setUp() {
        fauxnance = mock(FauxnanceApiClient.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        cache = new MarketDataCache(Duration.ofHours(24), clock);
        resolver = new QuoteResolver(fauxnance, cache, Duration.ofMinutes(10), clock);
    }

    private static QuotePayload cached(String symbol, String price) {
        return new QuotePayload(symbol, new BigDecimal(price), null, null, "USD", null, null, null, null, false, null);
    }

    private static FauxnanceApiClient.QuoteData live(String symbol, String price) {
        BigDecimal p = new BigDecimal(price);
        return new FauxnanceApiClient.QuoteData(symbol, p, p, p, "USD", NOW, false);
    }

    @Test
    @DisplayName("Recent cached quotes are used without calling Fauxnance")
    void recentCacheSkipsFauxnance() {
        cache.updateQuote("AAPL", cached("AAPL", "190.00"), NOW.minus(Duration.ofMinutes(4)));
        cache.updateQuote("FX:USDINR", cached("FX:USDINR", "83.47"), NOW.minus(Duration.ofMinutes(9)));

        Map<String, QuoteResolver.ResolvedQuote> result = resolver.resolve(List.of("AAPL", "FX:USDINR"));

        verifyNoInteractions(fauxnance);
        assertEquals(new BigDecimal("190.00"), result.get("AAPL").quote().getPrice());
        assertFalse(result.get("AAPL").stale());
        // Kafka quotes may lack bid/ask/observation time; they fall back to price and receivedAt
        assertEquals(new BigDecimal("190.00"), result.get("AAPL").quote().getBid());
        assertEquals(NOW.minus(Duration.ofMinutes(4)), result.get("AAPL").quote().getQuoteAsOf());
        assertEquals(new BigDecimal("83.47"), result.get("FX:USDINR").quote().getPrice());
    }

    @Test
    @DisplayName("Only missing or old symbols are fetched from Fauxnance")
    void fetchesOnlyMissingOrOld() {
        cache.updateQuote("AAPL", cached("AAPL", "190.00"), NOW.minus(Duration.ofMinutes(1)));
        cache.updateQuote("MSFT", cached("MSFT", "400.00"), NOW.minus(Duration.ofMinutes(30)));
        when(fauxnance.getBatchedQuotes(any())).thenReturn(Map.of(
            "MSFT", live("MSFT", "405.00"), "INFY.NS", live("INFY.NS", "1520.50")));

        Map<String, QuoteResolver.ResolvedQuote> result = resolver.resolve(List.of("AAPL", "MSFT", "INFY.NS"));

        verify(fauxnance).getBatchedQuotes(List.of("MSFT", "INFY.NS"));
        assertEquals(new BigDecimal("190.00"), result.get("AAPL").quote().getPrice());
        assertEquals(new BigDecimal("405.00"), result.get("MSFT").quote().getPrice());
        assertFalse(result.get("MSFT").stale());
        assertEquals(new BigDecimal("1520.50"), result.get("INFY.NS").quote().getPrice());
    }

    @Test
    @DisplayName("When Fauxnance fails, an old cached quote is used and marked stale")
    void oldCacheIsStaleFallback() {
        cache.updateQuote("MSFT", cached("MSFT", "400.00"), NOW.minus(Duration.ofHours(2)));
        when(fauxnance.getBatchedQuotes(any())).thenThrow(new RuntimeException("quota exhausted"));

        Map<String, QuoteResolver.ResolvedQuote> result = resolver.resolve(List.of("MSFT", "NOPE"));

        assertEquals(new BigDecimal("400.00"), result.get("MSFT").quote().getPrice());
        assertTrue(result.get("MSFT").stale());
        assertFalse(result.containsKey("NOPE"));
    }

    @Test
    @DisplayName("Empty input makes no calls")
    void emptyInput() {
        assertTrue(resolver.resolve(List.of()).isEmpty());
        verifyNoInteractions(fauxnance);
    }
}
