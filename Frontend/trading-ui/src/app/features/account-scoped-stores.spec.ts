import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CoreAuthService } from '../core/auth/core-auth.service';
import { NotificationService } from '../core/notifications/notification.service';
import { StrategiesService } from '../api/generated/strategy';
import { PriceAlertsService, WatchlistsService } from '../api/generated/watchlist';
import { StrategiesStore, messageOf } from './strategies/strategies.store';
import { WatchlistStore } from './watchlist/watchlist.store';
import { PriceAlertsStore } from './watchlist/alerts/price-alerts.store';

/**
 * The stores that hold one account's data: they forget it when a different user signs in, do
 * nothing while signed out, and turn server errors into readable messages.
 */
describe('account-scoped stores', () => {
  const user = signal<{ accountId: number } | null>({ accountId: 7 });
  const strategiesApi = {
    getStrategies: vi.fn(() => of([{ strategyId: 1, active: true }])),
    getStrategyExecutions: vi.fn(() => of([{ executionId: 9 }])),
  };
  const watchlistsApi = {
    getWatchlists: vi.fn(() => of([{ watchlistId: 1, name: 'Tech', items: [] }])),
  };
  const alertsApi = {
    getPriceAlerts: vi.fn(() => of([{ alertId: 1, active: true, symbol: 'AAPL' }])),
    getPriceAlertTriggers: vi.fn(() => of([{ triggerId: 3 }])),
  };

  beforeEach(() => {
    user.set({ accountId: 7 });
    TestBed.configureTestingModule({
      providers: [
        { provide: CoreAuthService, useValue: { currentUser: user } },
        { provide: NotificationService, useValue: { push: vi.fn() } },
        { provide: StrategiesService, useValue: strategiesApi },
        { provide: WatchlistsService, useValue: watchlistsApi },
        { provide: PriceAlertsService, useValue: alertsApi },
      ],
    });
  });

  afterEach(() => TestBed.resetTestingModule());

  const switchUser = (next: { accountId: number } | null) => {
    user.set(next);
    TestBed.tick();
  };

  it('strategies are cleared for the next user and need a signed-in account', async () => {
    const store = TestBed.inject(StrategiesStore);
    TestBed.tick();
    store.load();
    expect(store.strategies()).toHaveLength(1);

    switchUser({ accountId: 8 });
    expect(store.strategies()).toEqual([]);
    expect(store.isLoaded()).toBe(false);

    expect(await firstValueFrom(store.executions(1))).toEqual([{ executionId: 9 }]);

    switchUser(null);
    store.load();
    expect(strategiesApi.getStrategies).toHaveBeenCalledTimes(1);
    await expect(firstValueFrom(store.executions(1))).rejects.toThrow('signed in');
  });

  it('watchlists are cleared for the next user and refuse changes while signed out', async () => {
    const store = TestBed.inject(WatchlistStore);
    TestBed.tick();
    store.load();
    expect(store.watchlists()).toHaveLength(1);
    await expect(firstValueFrom(store.removeSymbol(1, 'AAPL'))).rejects.toThrow('AAPL is not in this watchlist.');

    switchUser({ accountId: 8 });
    expect(store.watchlists()).toEqual([]);

    switchUser(null);
    await expect(firstValueFrom(store.create('Growth'))).rejects.toThrow('signed in');
  });

  it('price alerts are cleared for the next user and need a signed-in account', async () => {
    const store = TestBed.inject(PriceAlertsStore);
    TestBed.tick();
    store.load();
    expect(store.alerts()).toHaveLength(1);
    expect(await firstValueFrom(store.triggers(1))).toEqual([{ triggerId: 3 }]);

    switchUser({ accountId: 8 });
    expect(store.alerts()).toEqual([]);

    switchUser(null);
    store.load();
    expect(alertsApi.getPriceAlerts).toHaveBeenCalledTimes(1);
    await expect(firstValueFrom(store.triggers(1))).rejects.toThrow('signed in');
  });

  it('turns trading-service errors into readable messages', async () => {
    const http = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

    expect(messageOf(http(404, { errorCode: 'VAL-422' }), 'x')).toContain('not available on the trading service');
    expect(messageOf(http(404, { errorCode: 'STR-404', message: 'Strategy 3 not found' }), 'x')).toBe('Strategy 3 not found');
    expect(messageOf(http(0), 'x')).toBe('Cannot reach the trading service.');
    expect(messageOf(http(500), 'fallback')).toBe('fallback');
    expect(messageOf(new Error('plain'), 'fallback')).toBe('fallback');

    alertsApi.getPriceAlertTriggers.mockReturnValueOnce(throwError(() => http(500)));
    const store = TestBed.inject(PriceAlertsStore);
    await expect(firstValueFrom(store.triggers(1))).rejects.toThrow(/./);
  });
});
