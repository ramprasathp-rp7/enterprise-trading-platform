package com.tradingplatform.api.controllers;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.dto.CachedQuoteResponse;
import com.tradingplatform.domain.dto.CachedQuotesDayResponse;
import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.dto.QuoteDayRequest;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.QuoteNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarketDataController.
 *
 * Tests cover:
 * - Latest quote: newest cached quote returned, symbol normalised, MKT-404 when missing
 * - Day rows: default today (UTC), explicit date, symbol filter, day boundaries
 *
 * Uses a real MarketDataCache with a long history window so fixed test times are kept.
 */
@DisplayName("MarketDataController Tests")
class MarketDataControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private MarketDataCache cache;
    private MarketDataController controller;

    @BeforeEach
    void setUp() {
        cache = new MarketDataCache(Duration.ofDays(3650));
        controller = new MarketDataController(cache, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should return the newest cached quote for a symbol")
    void testLatestQuote() {
        // Arrange
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1020.50"), Instant.parse("2026-10-06T09:00:00Z"));
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1021.75"), Instant.parse("2026-10-06T09:05:00Z"));

        // Act
        ResponseEntity<CachedQuoteResponse> response = controller.getLatestQuote("INFY.NS");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        CachedQuoteResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INFY.NS", body.symbol());
        assertEquals(new BigDecimal("1021.75"), body.price());
        assertEquals(new BigDecimal("1021.50"), body.bid());
        assertEquals(new BigDecimal("1022.00"), body.ask());
        assertEquals("INR", body.currency());
        assertEquals(Instant.parse("2026-10-06T09:05:00Z"), body.receivedAt());
    }

    @Test
    @DisplayName("Should match the symbol case-insensitively and ignore surrounding spaces")
    void testLatestQuoteNormalisesSymbol() {
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1020.50"), NOW);

        ResponseEntity<CachedQuoteResponse> response = controller.getLatestQuote(" infy.ns ");

        assertEquals(new BigDecimal("1020.50"), response.getBody().price());
    }

    @Test
    @DisplayName("Should raise MKT-404 when the cache has no quote, mapped to HTTP 404")
    void testLatestQuoteMissing() {
        // Act
        QuoteNotFoundException ex = assertThrows(QuoteNotFoundException.class,
            () -> controller.getLatestQuote("AAPL"));

        // Assert
        assertEquals("AAPL", ex.getSymbol());
        assertEquals(ErrorCode.MKT_404, ex.getErrorCode());

        ResponseEntity<ErrorResponse> error = new GlobalExceptionHandler()
            .handleQuoteNotFound(ex, new ServletWebRequest(new MockHttpServletRequest()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(ErrorCode.MKT_404, error.getBody().errorCode());
        assertEquals("No price is available for this symbol yet.", error.getBody().message());
    }

    @Test
    @DisplayName("Should return today's rows for all symbols, oldest first, by default")
    void testDayRowsDefaultToday() {
        // Arrange
        cache.updateQuote("AAPL", quote("AAPL", "190.00"), Instant.parse("2026-10-05T23:59:59Z"));  // yesterday
        cache.updateQuote("AAPL", quote("AAPL", "191.00"), Instant.parse("2026-10-06T00:00:00Z"));  // midnight
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1020.50"), Instant.parse("2026-10-06T09:00:00Z"));
        cache.updateQuote("AAPL", quote("AAPL", "192.00"), Instant.parse("2026-10-06T09:00:00Z"));
        cache.updateQuote("AAPL", quote("AAPL", "193.00"), Instant.parse("2026-10-07T00:00:00Z"));  // tomorrow

        // Act
        ResponseEntity<CachedQuotesDayResponse> response = controller.getQuotesForDay(null, null, null);

        // Assert
        CachedQuotesDayResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(LocalDate.parse("2026-10-06"), body.date());
        assertNull(body.symbol());
        assertEquals(3, body.count());
        assertEquals(List.of("AAPL", "AAPL", "INFY.NS"),
            body.quotes().stream().map(CachedQuoteResponse::symbol).toList());
        assertEquals(List.of(new BigDecimal("191.00"), new BigDecimal("192.00"), new BigDecimal("1020.50")),
            body.quotes().stream().map(CachedQuoteResponse::price).toList());
    }

    @Test
    @DisplayName("Should filter rows by date and symbol")
    void testDayRowsWithFilters() {
        // Arrange
        cache.updateQuote("AAPL", quote("AAPL", "190.00"), Instant.parse("2026-10-05T10:00:00Z"));
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1019.00"), Instant.parse("2026-10-05T10:00:00Z"));
        cache.updateQuote("AAPL", quote("AAPL", "191.00"), Instant.parse("2026-10-06T10:00:00Z"));

        // Act
        ResponseEntity<CachedQuotesDayResponse> response =
            controller.getQuotesForDay(LocalDate.parse("2026-10-05"), "aapl", null);

        // Assert
        CachedQuotesDayResponse body = response.getBody();
        assertEquals(LocalDate.parse("2026-10-05"), body.date());
        assertEquals("AAPL", body.symbol());
        assertEquals(1, body.count());
        assertEquals(new BigDecimal("190.00"), body.quotes().get(0).price());
    }

    @Test
    @DisplayName("Should return an empty list for a day or symbol with no rows")
    void testDayRowsEmpty() {
        cache.updateQuote("AAPL", quote("AAPL", "190.00"), NOW);

        CachedQuotesDayResponse otherDay = controller.getQuotesForDay(LocalDate.parse("2026-09-30"), null, null).getBody();
        CachedQuotesDayResponse otherSymbol = controller.getQuotesForDay(null, "MSFT", null).getBody();
        CachedQuotesDayResponse blankSymbol = controller.getQuotesForDay(null, " ", null).getBody();

        assertEquals(0, otherDay.count());
        assertTrue(otherDay.quotes().isEmpty());
        assertEquals(0, otherSymbol.count());
        assertNull(blankSymbol.symbol());
        assertEquals(1, blankSymbol.count());
    }

    @Test
    @DisplayName("Should take symbol and date from a JSON body when no query parameters are sent")
    void testDayRowsFromBody() {
        // Arrange
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1006.35"), Instant.parse("2026-10-05T09:00:00Z"));
        cache.updateQuote("RELIANCE.NS", quote("RELIANCE.NS", "1215.70"), Instant.parse("2026-10-05T09:00:00Z"));

        // Act
        CachedQuotesDayResponse body = controller.getQuotesForDay(null, null,
            new QuoteDayRequest("infy.ns", LocalDate.parse("2026-10-05"))).getBody();

        // Assert
        assertEquals("INFY.NS", body.symbol());
        assertEquals(LocalDate.parse("2026-10-05"), body.date());
        assertEquals(1, body.count());
        assertEquals("INFY.NS", body.quotes().get(0).symbol());
    }

    @Test
    @DisplayName("Should let query parameters win over the JSON body")
    void testQueryParamsOverrideBody() {
        cache.updateQuote("INFY.NS", quote("INFY.NS", "1006.35"), NOW);
        cache.updateQuote("RELIANCE.NS", quote("RELIANCE.NS", "1215.70"), NOW);

        CachedQuotesDayResponse body = controller.getQuotesForDay(null, "RELIANCE.NS",
            new QuoteDayRequest("INFY.NS", LocalDate.parse("2026-09-30"))).getBody();

        assertEquals("RELIANCE.NS", body.symbol());
        assertEquals(LocalDate.parse("2026-09-30"), body.date());   // date still from body
        assertEquals(0, body.count());
    }

    private static QuotePayload quote(String symbol, String price) {
        BigDecimal p = new BigDecimal(price);
        String currency = symbol.endsWith(".NS") ? "INR" : "USD";
        return new QuotePayload(symbol, p, p.subtract(new BigDecimal("0.25")), p.add(new BigDecimal("0.25")),
            currency, null, null, null, null, false, Instant.parse("2026-10-06T08:59:00Z"));
    }
}
