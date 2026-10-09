package com.tradingplatform.watchlist.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.AccountUnauthorizedException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.watchlist.alerts.controller.PriceAlertController;
import com.tradingplatform.watchlist.alerts.dto.CreatePriceAlertRequest;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertResponse;
import com.tradingplatform.watchlist.alerts.dto.UpdatePriceAlertRequest;
import com.tradingplatform.watchlist.alerts.evaluator.ActiveAlertIndex;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.service.PriceAlertService;

class PriceAlertControllerTest {

    private static final long ACCOUNT = 5L;
    private static final long OTHER_ACCOUNT = 6L;

    private PriceAlertController controller;

    @BeforeEach
    void setUp() {
        FakePriceAlertStore store = new FakePriceAlertStore();
        InstrumentMapper instruments = new InstrumentMapper() {
            private final Map<String, Instrument> bySymbol =
                Map.of("AAPL", Instrument.tradable(1L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD"));
            @Override public Instrument findBySymbol(String symbol) { return bySymbol.get(symbol); }
            @Override public Instrument findById(Long id) { return null; }
            @Override public List<Instrument> findAllInstruments() { return List.copyOf(bySymbol.values()); }
            @Override public List<Instrument> findTradableInstruments() {
                return bySymbol.values().stream().filter(Instrument::isTradable).toList();
            }
            @Override public List<String> findAllSymbols() { return List.copyOf(bySymbol.keySet()); }
            @Override public List<String> findConversionFxSymbols() { return List.of(); }
            @Override public List<com.tradingplatform.instruments.model.InstrumentListing> findAllListings() { return List.of(); }
            @Override public com.tradingplatform.instruments.model.InstrumentListing findListingBySymbol(String symbol) { return null; }
            @Override public int insertDiscovered(com.tradingplatform.instruments.model.InstrumentListing instrument) { return 0; }
        };
        PriceAlertService service = new PriceAlertService(store, store, instruments, new ActiveAlertIndex(), 20, 100);
        controller = new PriceAlertController(service, new AccountAccessAuthorizer());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Owner can create, list, pause and delete; create returns 201")
    void ownerCanManageAlerts() {
        signInAs(ACCOUNT);

        ResponseEntity<PriceAlertResponse> created = controller.createPriceAlert(ACCOUNT, aaplAbove190());
        long alertId = created.getBody().alertId();

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(1, controller.getPriceAlerts(ACCOUNT, null, null).getBody().size());
        assertEquals(false, controller.updatePriceAlert(ACCOUNT, alertId, new UpdatePriceAlertRequest(false)).getBody().active());
        assertEquals(HttpStatus.NO_CONTENT, controller.deletePriceAlert(ACCOUNT, alertId).getStatusCode());
    }

    @Test
    @DisplayName("Every route rejects a token for another account with ACC-403")
    void everyRouteIsAuthorised() {
        signInAs(OTHER_ACCOUNT);

        List<Executable> routes = List.of(
            () -> controller.getPriceAlerts(ACCOUNT, null, null),
            () -> controller.createPriceAlert(ACCOUNT, aaplAbove190()),
            () -> controller.getRecentPriceAlertTriggers(ACCOUNT, null),
            () -> controller.getPriceAlert(ACCOUNT, 1L),
            () -> controller.updatePriceAlert(ACCOUNT, 1L, new UpdatePriceAlertRequest(false)),
            () -> controller.deletePriceAlert(ACCOUNT, 1L),
            () -> controller.getPriceAlertTriggers(ACCOUNT, 1L));

        for (Executable route : routes) {
            AccountUnauthorizedException ex = assertThrows(AccountUnauthorizedException.class, route);
            assertEquals("ACC-403", ex.getCode());
        }
    }

    private static CreatePriceAlertRequest aaplAbove190() {
        return new CreatePriceAlertRequest("AAPL", AlertDirection.ABOVE, new BigDecimal("190"), AlertMode.ONCE);
    }

    private static void signInAs(long accountId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("accountId", accountId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
