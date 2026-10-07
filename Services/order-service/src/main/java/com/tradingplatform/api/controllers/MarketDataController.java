package com.tradingplatform.api.controllers;

import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.dto.CachedQuoteResponse;
import com.tradingplatform.domain.dto.CachedQuotesDayResponse;
import com.tradingplatform.domain.dto.QuoteDayRequest;
import com.tradingplatform.domain.exception.QuoteNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Market data REST API endpoints.
 * Reads quotes from the in-memory MarketDataCache (filled by the Fauxnance refresh or the
 * market-data topic, see QuoteSource). Never calls Fauxnance, so it costs no quota.
 * Requires an authenticated token; not scoped to an account.
 */
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    private final MarketDataCache marketDataCache;
    private final Clock clock;

    @Autowired
    public MarketDataController(MarketDataCache marketDataCache) {
        this(marketDataCache, Clock.systemUTC());
    }

    MarketDataController(MarketDataCache marketDataCache, Clock clock) {
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * GET /api/v1/market-data/quotes/{symbol}/latest
     * Returns the newest cached quote (price, bid, ask, timestamps) for a symbol.
     * 404 MKT-404 if the cache has no quote for it.
     */
    @GetMapping("/quotes/{symbol}/latest")
    public ResponseEntity<CachedQuoteResponse> getLatestQuote(@PathVariable String symbol) {
        String normalised = normalise(symbol);
        List<MarketDataCache.CachedQuote> history = marketDataCache.getQuoteHistory(normalised);
        if (history.isEmpty()) {
            throw new QuoteNotFoundException(normalised);
        }
        return ResponseEntity.ok(CachedQuoteResponse.from(history.get(history.size() - 1)));
    }

    /**
     * GET /api/v1/market-data/quotes
     * Returns the cached quote rows received on one UTC day, oldest first.
     * Optional query parameters (or the same fields in a JSON body; query parameters win):
     *   - date: LocalDate - the UTC day (default: today)
     *   - symbol: String - restrict to one instrument (default: all symbols)
     * Only rows still held by the cache are returned (see market-data.cache.history-window).
     */
    @GetMapping("/quotes")
    public ResponseEntity<CachedQuotesDayResponse> getQuotesForDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String symbol,
            @RequestBody(required = false) QuoteDayRequest body) {
        LocalDate requestedDate = date != null ? date : body != null ? body.date() : null;
        String requestedSymbol = symbol != null ? symbol : body != null ? body.symbol() : null;

        LocalDate day = requestedDate != null ? requestedDate : LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String normalised = requestedSymbol == null || requestedSymbol.isBlank() ? null : normalise(requestedSymbol);

        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CachedQuoteResponse> rows = marketDataCache.getQuotesReceivedBetween(normalised, from, to).stream()
            .map(CachedQuoteResponse::from)
            .toList();

        return ResponseEntity.ok(new CachedQuotesDayResponse(day, normalised, rows.size(), rows));
    }

    /** Symbols are stored upper case (e.g. INFY.NS, FX:EURUSD). */
    private static String normalise(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
