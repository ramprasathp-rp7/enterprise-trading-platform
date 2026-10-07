package com.tradingplatform.advice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradingplatform.advice.controller.TradeAdviceController;
import com.tradingplatform.advice.dto.SignalResponse;
import com.tradingplatform.advice.dto.TradeAdviceResponse;
import com.tradingplatform.advice.engine.Signal;
import com.tradingplatform.advice.service.TradeAdviceService;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.CandleStorageService;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.AccountUnauthorizedException;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.watchlist.mapper.WatchlistMapper;
import com.tradingplatform.watchlist.model.WatchlistItem;

/**
 * Unit tests for TradeAdviceService and TradeAdviceController.
 *
 * Execution paths:
 * - signal generated with reasoning (held and watched instruments, merged)
 * - no signal when data is insufficient
 * - unauthorised access rejected
 */
@DisplayName("Trade Advice service and API Tests")
class TradeAdviceServiceTest {

    private static final long ACCOUNT = 7L;

    private AccountMapper accountMapper;
    private InstrumentMapper instrumentMapper;
    private WatchlistMapper watchlistMapper;
    private CandleStorageService candles;
    private MarketDataCache cache;
    private TradeAdviceService service;

    private final Instrument infy = Instrument.tradable(1L, "INFY.NS", "Infosys Limited", AssetClass.EQUITY, "INR");
    private final Instrument aapl = Instrument.tradable(2L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD");
    private final Instrument acme = Instrument.tradable(3L, "ACME", "Acme Corporation", AssetClass.EQUITY, "USD");

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        instrumentMapper = mock(InstrumentMapper.class);
        watchlistMapper = mock(WatchlistMapper.class);
        candles = mock(CandleStorageService.class);
        cache = new MarketDataCache();
        service = new TradeAdviceService(accountMapper, instrumentMapper, watchlistMapper, candles, cache);

        when(instrumentMapper.findById(1L)).thenReturn(infy);
        when(instrumentMapper.findById(2L)).thenReturn(aapl);
        when(instrumentMapper.findBySymbol("INFY.NS")).thenReturn(infy);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(aapl);
        when(instrumentMapper.findBySymbol("ACME")).thenReturn(acme);

        // INFY.NS: downtrend; AAPL: uptrend; ACME: only 10 days of history
        when(candles.getLatestCandles(eq(1L), anyInt())).thenReturn(series(1L, 140, -1));
        when(candles.getLatestCandles(eq(2L), anyInt())).thenReturn(series(2L, 100, +1));
        when(candles.getLatestCandles(eq(3L), anyInt())).thenReturn(series(3L, 100, +1).subList(0, 10));
    }

    @Test
    @DisplayName("Signal generated with reasoning for every held and watched instrument, merged and ordered")
    void testSignalsForHoldingsAndWatchlist() {
        // Arrange: holds INFY.NS; watches INFY.NS and AAPL
        when(accountMapper.findPositionByAccountId(ACCOUNT)).thenReturn(List.of(
            new Position(ACCOUNT, 1L, 40, new BigDecimal("120.00"), 1)));
        when(watchlistMapper.findItemsByAccountId(ACCOUNT)).thenReturn(List.of(item("INFY.NS"), item("AAPL")));

        // Act
        TradeAdviceResponse advice = service.adviceFor(ACCOUNT);

        // Assert
        assertEquals(ACCOUNT, advice.accountId());
        assertNotNull(advice.generatedAt());
        assertTrue(advice.disclaimer().contains("not investment advice"));
        assertEquals(List.of("INFY.NS", "AAPL"), advice.signals().stream().map(SignalResponse::symbol).toList());

        SignalResponse infySignal = advice.signals().get(0);
        assertEquals(Signal.SELL, infySignal.signal());
        assertTrue(infySignal.held());
        assertTrue(infySignal.watched());
        assertEquals("Infosys Limited", infySignal.name());
        assertTrue(infySignal.summary().startsWith("Signals favour selling or reducing"), infySignal.summary());
        assertFalse(infySignal.reasons().isEmpty());
        assertNotNull(infySignal.indicators().unrealisedPercent());

        SignalResponse aaplSignal = advice.signals().get(1);
        assertEquals(Signal.BUY, aaplSignal.signal());
        assertFalse(aaplSignal.held());
        assertTrue(aaplSignal.watched());
        assertNull(aaplSignal.indicators().unrealisedPercent());
    }

    @Test
    @DisplayName("No signal when data is insufficient: the instrument is listed with the reason, after the signals")
    void testInsufficientData() {
        when(accountMapper.findPositionByAccountId(ACCOUNT)).thenReturn(List.of());
        when(watchlistMapper.findItemsByAccountId(ACCOUNT)).thenReturn(List.of(item("ACME"), item("AAPL")));

        TradeAdviceResponse advice = service.adviceFor(ACCOUNT);

        assertEquals(List.of("AAPL", "ACME"), advice.signals().stream().map(SignalResponse::symbol).toList());
        SignalResponse acmeSignal = advice.signals().get(1);
        assertNull(acmeSignal.signal());
        assertNull(acmeSignal.confidence());
        assertEquals("Only 10 days of price history; at least 50 are needed for a reliable signal.", acmeSignal.summary());
        assertEquals(10, acmeSignal.indicators().daysOfHistory());
    }

    @Test
    @DisplayName("Uses the cached live quote as the current price, and skips synthetic candles")
    void testLiveQuoteAndSyntheticCandles() {
        when(accountMapper.findPositionByAccountId(ACCOUNT)).thenReturn(List.of());
        when(watchlistMapper.findItemsByAccountId(ACCOUNT)).thenReturn(List.of(item("AAPL")));
        List<CandleData> withSynthetic = new ArrayList<>(series(2L, 100, +1));
        withSynthetic.add(candle(2L, LocalDate.of(2026, 10, 6), 999, true));
        when(candles.getLatestCandles(eq(2L), anyInt())).thenReturn(withSynthetic);
        cache.updateQuote("AAPL", new com.tradingplatform.api.events.QuotePayload("AAPL", new BigDecimal("161.50"),
            null, null, "USD", null, null, null, null, false, null), null);

        SignalResponse aaplSignal = service.adviceFor(ACCOUNT).signals().get(0);

        assertEquals(0, new BigDecimal("161.50").compareTo(aaplSignal.indicators().price()));
        assertEquals(60, aaplSignal.indicators().daysOfHistory());
    }

    @Test
    @DisplayName("An account with nothing held or watched gets an empty list")
    void testNothingToAdvise() {
        when(accountMapper.findPositionByAccountId(ACCOUNT)).thenReturn(List.of());
        when(watchlistMapper.findItemsByAccountId(ACCOUNT)).thenReturn(List.of());

        assertTrue(service.adviceFor(ACCOUNT).signals().isEmpty());
    }

    @Test
    @DisplayName("Unauthorised access rejected: another account's advice is refused before anything is read")
    void testUnauthorisedRejected() {
        AccountAccessAuthorizer authorizer = mock(AccountAccessAuthorizer.class);
        doThrow(new AccountUnauthorizedException(8L, ACCOUNT)).when(authorizer).assertTokenCanAccess(8L);
        TradeAdviceService spyService = mock(TradeAdviceService.class);
        TradeAdviceController controller = new TradeAdviceController(spyService, authorizer);

        assertThrows(AccountUnauthorizedException.class, () -> controller.getAdvice(8L));
        verify(spyService, never()).adviceFor(anyLong());
    }

    @Test
    @DisplayName("Returns the advice for the caller's own account")
    void testAuthorisedReturnsAdvice() {
        AccountAccessAuthorizer authorizer = mock(AccountAccessAuthorizer.class);
        when(accountMapper.findPositionByAccountId(ACCOUNT)).thenReturn(List.of());
        when(watchlistMapper.findItemsByAccountId(ACCOUNT)).thenReturn(List.of(item("AAPL")));
        TradeAdviceController controller = new TradeAdviceController(service, authorizer);

        TradeAdviceResponse body = controller.getAdvice(ACCOUNT).getBody();

        verify(authorizer).assertTokenCanAccess(ACCOUNT);
        assertEquals(1, body.signals().size());
    }

    // ----- fixtures -----

    /** 60 daily candles: 40 trending days from start by step, then 20 days drifting the same way with swings */
    private static List<CandleData> series(long instrumentId, double start, int step) {
        List<CandleData> list = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 7, 1);
        for (int i = 0; i < 40; i++) {
            list.add(candle(instrumentId, day.plusDays(i), start + step * i, false));
        }
        double base = start + step * 40;
        for (int i = 0; i < 20; i++) {
            list.add(candle(instrumentId, day.plusDays(40 + i), base + step * i * 0.3 + (i % 2 == 0 ? 1.5 : -1.5), false));
        }
        return list;
    }

    private static CandleData candle(long instrumentId, LocalDate date, double close, boolean synthetic) {
        BigDecimal c = BigDecimal.valueOf(close);
        return new CandleData(null, instrumentId, date, c, c, c, c, c, 1000L, synthetic, null, null);
    }

    private static WatchlistItem item(String symbol) {
        WatchlistItem item = new WatchlistItem();
        item.setSymbol(symbol);
        item.setAccountId(ACCOUNT);
        return item;
    }

    @SuppressWarnings("unused")
    private static Optional<BigDecimal> none() {
        return Optional.empty();
    }
}
