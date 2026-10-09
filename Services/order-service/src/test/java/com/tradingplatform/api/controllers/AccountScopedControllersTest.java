package com.tradingplatform.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.HealthService;
import com.tradingplatform.api.services.PortfolioPnlService;
import com.tradingplatform.domain.dto.HealthResponse;
import com.tradingplatform.domain.dto.PnlResponse;
import com.tradingplatform.domain.dto.PortfolioSummary;
import com.tradingplatform.domain.dto.PricedPosition;
import com.tradingplatform.notifications.controller.NotificationHistoryController;
import com.tradingplatform.notifications.dto.NotificationHistoryItem;
import com.tradingplatform.notifications.service.NotificationHistoryService;
import com.tradingplatform.strategy.controller.StrategyController;
import com.tradingplatform.strategy.dto.StrategyExecutionResponse;
import com.tradingplatform.strategy.dto.StrategyResponse;
import com.tradingplatform.strategy.service.StrategyService;
import com.tradingplatform.watchlist.alerts.controller.PriceAlertController;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertResponse;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertTriggerResponse;
import com.tradingplatform.watchlist.alerts.service.PriceAlertService;
import com.tradingplatform.watchlist.controller.WatchlistController;
import com.tradingplatform.watchlist.dto.AddWatchlistItemRequest;
import com.tradingplatform.watchlist.dto.CreateWatchlistRequest;
import com.tradingplatform.watchlist.dto.WatchlistItemResponse;
import com.tradingplatform.watchlist.dto.WatchlistResponse;
import com.tradingplatform.watchlist.service.WatchlistService;

/**
 * Controllers scoped to one account: every endpoint checks the caller may see the account, then
 * answers from its service.
 */
class AccountScopedControllersTest {

    private final AccountAccessAuthorizer authorizer = mock(AccountAccessAuthorizer.class);

    @Test
    void watchlists() {
        WatchlistService service = mock(WatchlistService.class);
        WatchlistController controller = new WatchlistController(service, authorizer);
        WatchlistResponse list = mock(WatchlistResponse.class);
        WatchlistItemResponse item = mock(WatchlistItemResponse.class);
        when(service.getWatchlists(1L)).thenReturn(List.of(list));
        when(service.createWatchlist(1L, "Tech")).thenReturn(list);
        when(service.getWatchlist(1L, 5L)).thenReturn(list);
        when(service.addItem(1L, 5L, "AAPL")).thenReturn(item);

        assertThat(controller.getWatchlists(1L).getBody()).containsExactly(list);
        ResponseEntity<WatchlistResponse> created = controller.createWatchlist(1L, new CreateWatchlistRequest("Tech"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isSameAs(list);
        assertThat(controller.getWatchlist(1L, 5L).getBody()).isSameAs(list);
        ResponseEntity<WatchlistItemResponse> added = controller.addItem(1L, 5L, new AddWatchlistItemRequest("AAPL"));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(added.getBody()).isSameAs(item);
        assertThat(controller.removeItem(1L, 5L, 9L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.deleteWatchlist(1L, 5L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(service).removeItem(1L, 5L, 9L);
        verify(service).deleteWatchlist(1L, 5L);
        verify(authorizer, org.mockito.Mockito.times(6)).assertTokenCanAccess(1L);
    }

    @Test
    void notificationHistory() {
        NotificationHistoryService service = mock(NotificationHistoryService.class);
        NotificationHistoryItem item = mock(NotificationHistoryItem.class);
        when(service.history(1L, 50)).thenReturn(List.of(item));

        assertThat(new NotificationHistoryController(service, authorizer).getNotificationHistory(1L, 50).getBody())
            .containsExactly(item);
        verify(authorizer).assertTokenCanAccess(1L);
    }

    @Test
    void portfolio() {
        PortfolioPnlService service = mock(PortfolioPnlService.class);
        PortfolioController controller = new PortfolioController(service, authorizer);
        PortfolioSummary summary = mock(PortfolioSummary.class);
        PricedPosition position = mock(PricedPosition.class);
        PnlResponse pnl = mock(PnlResponse.class);
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 9);
        when(service.getPortfolioSummary(1L)).thenReturn(summary);
        when(service.getPricedPositions(1L, "AAPL")).thenReturn(List.of(position));
        when(service.getPnl(1L, from, to, true)).thenReturn(pnl);

        assertThat(controller.getPortfolioSummary(1L).getBody()).isSameAs(summary);
        assertThat(controller.getPricedPositions(1L, "AAPL").getBody()).containsExactly(position);
        assertThat(controller.getPnl(1L, from, to, true).getBody()).isSameAs(pnl);
        verify(authorizer, org.mockito.Mockito.times(3)).assertTokenCanAccess(1L);
    }

    @Test
    void health() {
        HealthService service = mock(HealthService.class);
        HealthResponse health = new HealthResponse("UP", List.of(), Instant.now());
        when(service.getHealth()).thenReturn(health);

        ResponseEntity<HealthResponse> response = new HealthController(service).getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(health);
    }

    @Test
    void strategies() {
        StrategyService service = mock(StrategyService.class);
        StrategyController controller = new StrategyController(service, authorizer);
        StrategyResponse strategy = mock(StrategyResponse.class);
        StrategyExecutionResponse execution = mock(StrategyExecutionResponse.class);
        when(service.getStrategies(1L)).thenReturn(List.of(strategy));
        when(service.getStrategy(1L, 3L)).thenReturn(strategy);
        when(service.getExecutions(1L, 3L, 20)).thenReturn(List.of(execution));

        assertThat(controller.getStrategies(1L).getBody()).containsExactly(strategy);
        assertThat(controller.getStrategy(1L, 3L).getBody()).isSameAs(strategy);
        assertThat(controller.getExecutions(1L, 3L, 20).getBody()).containsExactly(execution);
        verify(authorizer, atLeastOnce()).assertTokenCanAccess(1L);
    }

    @Test
    void priceAlertsAndTheirTriggers() {
        PriceAlertService service = mock(PriceAlertService.class);
        PriceAlertController controller = new PriceAlertController(service, authorizer);
        PriceAlertResponse alert = mock(PriceAlertResponse.class);
        PriceAlertTriggerResponse trigger = mock(PriceAlertTriggerResponse.class);
        Instant since = Instant.parse("2026-10-09T00:00:00Z");
        when(service.getRecentTriggers(1L, since)).thenReturn(List.of(trigger));
        when(service.getAlert(1L, 7L)).thenReturn(alert);
        when(service.getTriggers(1L, 7L)).thenReturn(List.of(trigger));

        assertThat(controller.getRecentPriceAlertTriggers(1L, since).getBody()).containsExactly(trigger);
        assertThat(controller.getPriceAlert(1L, 7L).getBody()).isSameAs(alert);
        assertThat(controller.getPriceAlertTriggers(1L, 7L).getBody()).containsExactly(trigger);
        verify(authorizer, org.mockito.Mockito.times(3)).assertTokenCanAccess(1L);
    }
}
