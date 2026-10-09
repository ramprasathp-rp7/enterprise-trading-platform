package com.tradingplatform.instruments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.services.CandleSyncService;
import com.tradingplatform.api.services.FauxnanceApiClient;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.exception.MarketDataUnavailableException;
import com.tradingplatform.domain.exception.SymbolNotDiscoveredException;
import com.tradingplatform.instruments.model.InstrumentListing;
import com.tradingplatform.instruments.service.InstrumentCatalogService;

class InstrumentCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    private InstrumentMapper mapper;
    private FauxnanceApiClient fauxnance;
    private MarketDataCache cache;
    private CandleSyncService candles;
    private InstrumentCatalogService service;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(InstrumentMapper.class);
        fauxnance = mock(FauxnanceApiClient.class);
        cache = mock(MarketDataCache.class);
        candles = mock(CandleSyncService.class);
        // The package-private constructor takes a direct executor and a fixed clock
        Constructor<InstrumentCatalogService> ctor = InstrumentCatalogService.class.getDeclaredConstructor(
            InstrumentMapper.class, FauxnanceApiClient.class, MarketDataCache.class, CandleSyncService.class,
            Executor.class, Clock.class);
        ctor.setAccessible(true);
        service = ctor.newInstance(mapper, fauxnance, cache, candles, (Executor) Runnable::run,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void list_resolvesMissingExchangesFromTheSymbol() {
        when(mapper.findAllListings()).thenReturn(List.of(
            new InstrumentListing(1L, "AAPL", "Apple", "EQUITY", "USD", true, "NASDAQ"),
            new InstrumentListing(2L, "INFY.NS", "Infosys", "EQUITY", "INR", true, null)));

        List<InstrumentListing> all = service.list();

        assertEquals("NASDAQ", all.get(0).getExchange());
        assertEquals("NSE", all.get(1).getExchange());
    }

    @Test
    void discover_alreadyListed_returnsItWithoutCallingFauxnance() {
        InstrumentListing aapl = new InstrumentListing(2L, "AAPL", "Apple", "EQUITY", "USD", true, "NASDAQ");
        when(mapper.findListingBySymbol("AAPL")).thenReturn(aapl);

        InstrumentCatalogService.Discovery result = service.discover("  aapl ");

        assertFalse(result.created());
        assertSame(aapl, result.instrument());
        verifyNoInteractions(fauxnance);
    }

    @Test
    void discover_newSymbol_addsTheInstrumentCachesItsQuoteAndLoadsCandles() {
        when(fauxnance.findSymbol("SHOP")).thenReturn(Optional.of(
            new FauxnanceApiClient.SymbolInfo("SHOP", "Shopify Inc.", "equity", "NYSE", "USD", true)));
        when(fauxnance.getBatchedQuotes(List.of("SHOP"))).thenReturn(Map.of("SHOP", quote("SHOP", "151.20", "USD")));
        doAnswer(inv -> {
            inv.<InstrumentListing>getArgument(0).setId(26L);
            return 1;
        }).when(mapper).insertDiscovered(any());

        InstrumentCatalogService.Discovery result = service.discover("shop");

        assertTrue(result.created());
        InstrumentListing added = result.instrument();
        assertEquals(26L, added.getId());
        assertEquals("SHOP", added.getSymbol());
        assertEquals("Shopify Inc.", added.getName());
        assertEquals("EQUITY", added.getAssetClass());
        assertEquals("USD", added.getCurrency());
        assertEquals("NYSE", added.getExchange());
        assertTrue(added.isTradable());

        ArgumentCaptor<QuotePayload> cached = ArgumentCaptor.forClass(QuotePayload.class);
        verify(cache).updateQuote(eq("SHOP"), cached.capture(), eq(NOW));
        assertEquals(new BigDecimal("151.20"), cached.getValue().price());
        verify(candles).bootstrapCandleData(26L, "SHOP", LocalDate.of(2026, 7, 9), LocalDate.of(2026, 10, 8));
    }

    @Test
    void discover_mapsRegistryTypesAndFallsBackForUnknownExchanges() {
        when(fauxnance.findSymbol("X:DOGE-USD")).thenReturn(Optional.of(
            new FauxnanceApiClient.SymbolInfo("X:DOGE-USD", "Dogecoin / US Dollar", "crypto", "SOMEWHERE", null, false)));
        when(fauxnance.getBatchedQuotes(List.of("X:DOGE-USD"))).thenReturn(Map.of("X:DOGE-USD", quote("X:DOGE-USD", "0.21", "usd")));

        InstrumentListing added = service.discover("X:DOGE-USD").instrument();

        assertEquals("CRYPTO", added.getAssetClass());
        assertEquals("CRYPTO", added.getExchange());
        assertEquals("USD", added.getCurrency());
        assertFalse(added.isTradable(), "an inactive registry entry is listed as not tradable");
    }

    @Test
    void discover_unknownSymbol_isNotFoundAndNothingIsStored() {
        when(fauxnance.findSymbol("NOPE")).thenReturn(Optional.empty());

        assertThrows(SymbolNotDiscoveredException.class, () -> service.discover("NOPE"));
        verify(mapper, never()).insertDiscovered(any());
        verify(fauxnance, never()).getBatchedQuotes(any());
    }

    @Test
    void discover_knownButUnquoted_isNotFound() {
        when(fauxnance.findSymbol("ZZZ")).thenReturn(Optional.of(
            new FauxnanceApiClient.SymbolInfo("ZZZ", "Zed", "equity", "NASDAQ", "USD", true)));
        when(fauxnance.getBatchedQuotes(List.of("ZZZ"))).thenReturn(Map.of());

        assertThrows(SymbolNotDiscoveredException.class, () -> service.discover("ZZZ"));
        verify(mapper, never()).insertDiscovered(any());
    }

    @Test
    void discover_fauxnanceDown_isUnavailable() {
        when(fauxnance.findSymbol("SHOP")).thenThrow(new IllegalStateException("connect timed out"));

        assertThrows(MarketDataUnavailableException.class, () -> service.discover("SHOP"));
    }

    @Test
    void discover_rejectsSomethingThatCannotBeASymbol() {
        assertThrows(InvalidOrderException.class, () -> service.discover("not a symbol!"));
        assertThrows(InvalidOrderException.class, () -> service.discover(""));
        verifyNoInteractions(fauxnance);
    }

    @Test
    void discover_concurrentDiscovery_returnsTheRowThatWon() {
        when(fauxnance.findSymbol("SHOP")).thenReturn(Optional.of(
            new FauxnanceApiClient.SymbolInfo("SHOP", "Shopify Inc.", "equity", "NYSE", "USD", true)));
        when(fauxnance.getBatchedQuotes(List.of("SHOP"))).thenReturn(Map.of("SHOP", quote("SHOP", "151.20", "USD")));
        when(mapper.insertDiscovered(any())).thenThrow(new DuplicateKeyException("uq_instruments_symbol"));
        InstrumentListing winner = new InstrumentListing(30L, "SHOP", "Shopify Inc.", "EQUITY", "USD", true, "NYSE");
        when(mapper.findListingBySymbol("SHOP")).thenReturn(null, winner);

        InstrumentCatalogService.Discovery result = service.discover("SHOP");

        assertFalse(result.created());
        assertSame(winner, result.instrument());
        verify(cache, never()).updateQuote(any(), any(), any());
    }

    @Test
    void discover_candleFailureDoesNotFailTheDiscovery() {
        when(fauxnance.findSymbol("SHOP")).thenReturn(Optional.of(
            new FauxnanceApiClient.SymbolInfo("SHOP", "Shopify Inc.", "equity", "NYSE", "USD", true)));
        when(fauxnance.getBatchedQuotes(List.of("SHOP"))).thenReturn(Map.of("SHOP", quote("SHOP", "151.20", "USD")));
        org.mockito.Mockito.doThrow(new RuntimeException("candles down")).when(candles)
            .bootstrapCandleData(any(), any(), any(), any());

        assertTrue(service.discover("SHOP").created());
    }

    private static FauxnanceApiClient.QuoteData quote(String symbol, String price, String currency) {
        BigDecimal p = new BigDecimal(price);
        return new FauxnanceApiClient.QuoteData(symbol, p, p, p, currency, NOW, true, null, null, null, "closed");
    }
}
