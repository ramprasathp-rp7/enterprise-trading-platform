package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.mappers.InstrumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketDataRefreshScheduler.
 *
 * Tests cover:
 * - Refresh: every instrument quoted, every Fauxnance quote appended to the cache
 * - Failures: partial results, API down and database down keep existing cached quotes
 * - Quote source switch: scheduler and Kafka consumer bind to opposite modes
 *
 * Uses a real MarketDataCache so written quotes are checked end to end.
 */
@DisplayName("MarketDataRefreshScheduler Tests")
class MarketDataRefreshSchedulerTest {

    private static final Instant AS_OF = Instant.parse("2026-10-05T09:45:00Z");

    private FauxnanceApiClient fauxnanceClient;
    private InstrumentMapper instrumentMapper;
    private MarketDataCache cache;
    private MarketDataRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        fauxnanceClient = mock(FauxnanceApiClient.class);
        instrumentMapper = mock(InstrumentMapper.class);
        cache = new MarketDataCache();
        scheduler = new MarketDataRefreshScheduler(fauxnanceClient, instrumentMapper, cache);
        when(instrumentMapper.findAllSymbols()).thenReturn(List.of("AAPL", "INFY.NS"));
    }

    @Test
    @DisplayName("Should write every Fauxnance quote into the cache")
    void testRefreshWritesQuotes() {
        // Arrange
        when(fauxnanceClient.getBatchedQuotes(List.of("AAPL", "INFY.NS"))).thenReturn(Map.of(
            "AAPL", quote("AAPL", "190.10", "USD"),
            "INFY.NS", quote("INFY.NS", "1020.50", "INR")
        ));

        // Act
        scheduler.refreshQuotes();

        // Assert
        QuotePayload infy = cache.getLatestQuote("INFY.NS").orElseThrow();
        assertEquals(new BigDecimal("1020.50"), infy.price());
        assertEquals(new BigDecimal("1020.28"), infy.bid());
        assertEquals(new BigDecimal("1020.72"), infy.ask());
        assertEquals("INR", infy.currency());
        assertEquals(AS_OF, infy.quoteAsOf());
        assertFalse(infy.stale());
        assertEquals(new BigDecimal("-4.25"), infy.change());
        assertEquals(new BigDecimal("-0.41"), infy.changePercent());
        assertEquals(new BigDecimal("1024.75"), infy.previousClose());
        assertEquals("open", infy.marketState());
        assertEquals(2, cache.getCacheSize());
    }

    @Test
    @DisplayName("Should append each refresh to the history, newest last")
    void testRefreshAppends() {
        // Arrange
        when(fauxnanceClient.getBatchedQuotes(anyList()))
            .thenReturn(Map.of("AAPL", quote("AAPL", "190.10", "USD")))
            .thenReturn(Map.of("AAPL", quote("AAPL", "191.00", "USD")));

        // Act
        scheduler.refreshQuotes();
        scheduler.refreshQuotes();

        // Assert
        assertEquals(new BigDecimal("191.00"), cache.getLatestPrice("AAPL").orElseThrow());
        assertEquals(List.of(new BigDecimal("190.10"), new BigDecimal("191.00")),
            cache.getQuoteHistory("AAPL").stream().map(entry -> entry.quote().price()).toList());
    }

    @Test
    @DisplayName("Should keep the last cached quote for symbols Fauxnance misses")
    void testPartialResult() {
        // Arrange
        when(fauxnanceClient.getBatchedQuotes(anyList()))
            .thenReturn(Map.of("AAPL", quote("AAPL", "190.10", "USD"),
                               "INFY.NS", quote("INFY.NS", "1020.50", "INR")))
            .thenReturn(Map.of("AAPL", quote("AAPL", "191.00", "USD")));
        scheduler.refreshQuotes();

        // Act
        scheduler.refreshQuotes();

        // Assert
        assertEquals(new BigDecimal("191.00"), cache.getLatestPrice("AAPL").orElseThrow());
        assertEquals(new BigDecimal("1020.50"), cache.getLatestPrice("INFY.NS").orElseThrow());
        assertEquals(2, cache.getQuoteHistory("AAPL").size());
        assertEquals(1, cache.getQuoteHistory("INFY.NS").size());
    }

    @Test
    @DisplayName("Should keep cached quotes when Fauxnance is down")
    void testFauxnanceDown() {
        // Arrange
        when(fauxnanceClient.getBatchedQuotes(anyList()))
            .thenReturn(Map.of("AAPL", quote("AAPL", "190.10", "USD")))
            .thenThrow(new RuntimeException("connection refused"));
        scheduler.refreshQuotes();

        // Act & Assert
        assertDoesNotThrow(() -> scheduler.refreshQuotes());
        assertEquals(new BigDecimal("190.10"), cache.getLatestPrice("AAPL").orElseThrow());
    }

    @Test
    @DisplayName("Should skip the refresh when instruments cannot be loaded")
    void testDatabaseFailure() {
        when(instrumentMapper.findAllSymbols()).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> scheduler.refreshQuotes());

        verifyNoInteractions(fauxnanceClient);
        assertEquals(0, cache.getCacheSize());
    }

    @Test
    @DisplayName("Should not call Fauxnance when there are no instruments")
    void testNoInstruments() {
        when(instrumentMapper.findAllSymbols()).thenReturn(List.of());

        scheduler.refreshQuotes();

        verifyNoInteractions(fauxnanceClient);
        assertEquals(0, cache.getCacheSize());
    }

    @Test
    @DisplayName("Should bind the scheduler to FAUXNANCE (default) and the consumer to KAFKA")
    void testQuoteSourceSwitch() {
        ConditionalOnProperty schedulerMode =
            MarketDataRefreshScheduler.class.getAnnotation(ConditionalOnProperty.class);
        ConditionalOnProperty consumerMode =
            MarketDataConsumer.class.getAnnotation(ConditionalOnProperty.class);

        assertArrayEquals(new String[] {"market-data.quote-source"}, schedulerMode.name());
        assertEquals("FAUXNANCE", schedulerMode.havingValue());
        assertTrue(schedulerMode.matchIfMissing());

        assertArrayEquals(new String[] {"market-data.quote-source"}, consumerMode.name());
        assertEquals("KAFKA", consumerMode.havingValue());
        assertFalse(consumerMode.matchIfMissing());
    }

    private static FauxnanceApiClient.QuoteData quote(String symbol, String price, String currency) {
        BigDecimal p = new BigDecimal(price);
        return new FauxnanceApiClient.QuoteData(symbol, p, p.subtract(new BigDecimal("0.22")),
            p.add(new BigDecimal("0.22")), currency, AS_OF, false,
            new BigDecimal("-4.25"), new BigDecimal("-0.41"), new BigDecimal("1024.75"), "open");
    }
}
