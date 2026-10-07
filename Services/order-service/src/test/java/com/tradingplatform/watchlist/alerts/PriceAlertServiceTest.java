package com.tradingplatform.watchlist.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.watchlist.alerts.dto.CreatePriceAlertRequest;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertResponse;
import com.tradingplatform.watchlist.alerts.evaluator.ActiveAlertIndex;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertConflictException;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertNotFoundException;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;
import com.tradingplatform.watchlist.alerts.service.PriceAlertService;

class PriceAlertServiceTest {

    private static final long ACCOUNT = 1L;
    private static final long OTHER_ACCOUNT = 2L;
    private static final int MAX_ACTIVE = 3;
    private static final int MAX_TOTAL = 5;

    private FakePriceAlertStore store;
    private ActiveAlertIndex index;
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        store = new FakePriceAlertStore();
        index = new ActiveAlertIndex();
        InstrumentMapper instruments = new FakeInstrumentMapper(Map.of(
            "AAPL", Instrument.tradable(1L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD"),
            "MSFT", Instrument.tradable(2L, "MSFT", "Microsoft", AssetClass.EQUITY, "USD")));
        service = new PriceAlertService(store, store, instruments, index, MAX_ACTIVE, MAX_TOTAL);
    }

    @Test
    void createAlert_isScopedToAccountAndStartsActive() {
        PriceAlertResponse created = service.createAlert(ACCOUNT, request(" aapl ", "190", AlertMode.ONCE));

        assertEquals(ACCOUNT, created.accountId());
        assertEquals("AAPL", created.symbol());
        assertTrue(created.active());
        assertEquals(0, created.triggerCount());
        assertEquals(1, service.getAlerts(ACCOUNT, null, null).size());
        assertTrue(service.getAlerts(OTHER_ACCOUNT, null, null).isEmpty());
    }

    @Test
    void createAlert_watchesTheSymbolForTheEvaluator() {
        service.createAlert(ACCOUNT, request("AAPL", "190", AlertMode.ONCE));

        assertTrue(index.isWatched("AAPL"));
    }

    @Test
    void createAlert_rejectsUnknownSymbol() {
        assertThrows(InstrumentNotFoundException.class,
            () -> service.createAlert(ACCOUNT, request("NOPE", "10", AlertMode.ONCE)));
    }

    @Test
    void createAlert_rejectsNonPositiveThreshold() {
        assertThrows(InvalidOrderException.class,
            () -> service.createAlert(ACCOUNT, request("AAPL", "0", AlertMode.ONCE)));
    }

    @Test
    void createAlert_rejectsWhenActiveCapReached() {
        for (int i = 0; i < MAX_ACTIVE; i++) {
            service.createAlert(ACCOUNT, request("AAPL", String.valueOf(100 + i), AlertMode.ONCE));
        }

        PriceAlertConflictException ex = assertThrows(PriceAlertConflictException.class,
            () -> service.createAlert(ACCOUNT, request("AAPL", "999", AlertMode.ONCE)));
        assertEquals("ALR-409", ex.getCode());
        assertEquals(MAX_ACTIVE, store.countByAccountId(ACCOUNT));

        // The cap is per account
        service.createAlert(OTHER_ACCOUNT, request("AAPL", "999", AlertMode.ONCE));
    }

    @Test
    void createAlert_rejectsWhenTotalCapReachedEvenIfAllPaused() {
        for (int i = 0; i < MAX_TOTAL; i++) {
            PriceAlertResponse a = service.createAlert(ACCOUNT, request("AAPL", String.valueOf(100 + i), AlertMode.ONCE));
            service.setActive(ACCOUNT, a.alertId(), false);
        }

        assertThrows(PriceAlertConflictException.class,
            () -> service.createAlert(ACCOUNT, request("AAPL", "999", AlertMode.ONCE)));
    }

    @Test
    void createAlert_rejectsDuplicateActiveRule() {
        service.createAlert(ACCOUNT, request("AAPL", "190", AlertMode.ONCE));

        assertThrows(PriceAlertConflictException.class,
            () -> service.createAlert(ACCOUNT, request("AAPL", "190.000", AlertMode.ONCE)));
    }

    @Test
    void pauseFreesASlot_andResumeRespectsCap() {
        PriceAlertResponse first = service.createAlert(ACCOUNT, request("AAPL", "100", AlertMode.ONCE));
        service.createAlert(ACCOUNT, request("AAPL", "101", AlertMode.ONCE));
        service.createAlert(ACCOUNT, request("AAPL", "102", AlertMode.ONCE));

        PriceAlertResponse paused = service.setActive(ACCOUNT, first.alertId(), false);
        assertFalse(paused.active());
        assertEquals(PriceAlert.PAUSED, paused.deactivatedReason());

        service.createAlert(ACCOUNT, request("MSFT", "300", AlertMode.EVERY_QUOTE));

        assertThrows(PriceAlertConflictException.class, () -> service.setActive(ACCOUNT, first.alertId(), true));
    }

    @Test
    void otherAccountsAlertIsNotFound() {
        PriceAlertResponse created = service.createAlert(ACCOUNT, request("AAPL", "190", AlertMode.ONCE));

        assertThrows(PriceAlertNotFoundException.class, () -> service.getAlert(OTHER_ACCOUNT, created.alertId()));
        assertThrows(PriceAlertNotFoundException.class, () -> service.setActive(OTHER_ACCOUNT, created.alertId(), false));
        assertThrows(PriceAlertNotFoundException.class, () -> service.deleteAlert(OTHER_ACCOUNT, created.alertId()));
        assertThrows(PriceAlertNotFoundException.class, () -> service.getTriggers(OTHER_ACCOUNT, created.alertId()));
    }

    @Test
    void getAlerts_filtersByActiveAndSymbol() {
        PriceAlertResponse a = service.createAlert(ACCOUNT, request("AAPL", "190", AlertMode.ONCE));
        service.createAlert(ACCOUNT, request("MSFT", "300", AlertMode.ONCE));
        service.setActive(ACCOUNT, a.alertId(), false);

        List<PriceAlertResponse> active = service.getAlerts(ACCOUNT, true, null);
        List<PriceAlertResponse> aapl = service.getAlerts(ACCOUNT, null, "aapl");

        assertEquals(List.of("MSFT"), active.stream().map(PriceAlertResponse::symbol).toList());
        assertEquals(List.of(a.alertId()), aapl.stream().map(PriceAlertResponse::alertId).toList());
    }

    @Test
    void deleteAlert_removesIt() {
        PriceAlertResponse created = service.createAlert(ACCOUNT, request("AAPL", "190", AlertMode.ONCE));

        service.deleteAlert(ACCOUNT, created.alertId());

        assertTrue(service.getAlerts(ACCOUNT, null, null).isEmpty());
    }

    private static CreatePriceAlertRequest request(String symbol, String threshold, AlertMode mode) {
        return new CreatePriceAlertRequest(symbol, AlertDirection.ABOVE, new BigDecimal(threshold), mode);
    }

    private record FakeInstrumentMapper(Map<String, Instrument> bySymbol) implements InstrumentMapper {
        @Override
        public Instrument findBySymbol(String symbol) {
            return bySymbol.get(symbol);
        }

        @Override
        public Instrument findById(Long id) {
            return bySymbol.values().stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<Instrument> findAllInstruments() {
            return bySymbol.values().stream().toList();
        }

        @Override
        public List<Instrument> findTradableInstruments() {
            return bySymbol.values().stream().filter(Instrument::isTradable).toList();
        }

        @Override
        public List<String> findAllSymbols() {
            return bySymbol.keySet().stream().sorted().toList();
        }
    }
}
