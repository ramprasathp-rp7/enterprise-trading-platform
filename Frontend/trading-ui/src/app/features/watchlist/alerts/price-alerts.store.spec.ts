import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';
import { AlertDirection, AlertMode, PriceAlertResponse, PriceAlertsService } from '../../../api/generated/watchlist';
import { CoreAuthService } from '../../../core/auth/core-auth.service';
import { PriceAlertsStore } from './price-alerts.store';

const alert = (overrides: Partial<PriceAlertResponse> = {}): PriceAlertResponse => ({
  alertId: 1,
  accountId: 7,
  symbol: 'AAPL',
  direction: AlertDirection.Above,
  threshold: 190,
  mode: AlertMode.Once,
  active: true,
  triggerCount: 0,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  ...overrides,
});

describe('PriceAlertsStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let store: PriceAlertsStore;

  beforeEach(() => {
    api = {
      getPriceAlerts: vi.fn(() => of([alert()])),
      createPriceAlert: vi.fn((_: number, body: { symbol: string; threshold: number }) =>
        of(alert({ alertId: 2, symbol: body.symbol, threshold: body.threshold })),
      ),
      updatePriceAlert: vi.fn((_: number, alertId: number, body: { active: boolean }) =>
        of(alert({ alertId, active: body.active, deactivatedReason: body.active ? undefined : 'PAUSED' })),
      ),
      deletePriceAlert: vi.fn(() => of(null)),
      getPriceAlertTriggers: vi.fn(() => of([])),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: PriceAlertsService, useValue: api },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7 }) } },
      ],
    });
    store = TestBed.inject(PriceAlertsStore);
  });

  it('loads the signed-in account’s alerts and counts the active ones', () => {
    store.load();

    expect(api['getPriceAlerts']).toHaveBeenCalledWith(7);
    expect(store.alerts().map((a) => a.alertId)).toEqual([1]);
    expect(store.activeCount()).toBe(1);
    expect(store.hasActiveAlert('AAPL')).toBe(true);
    expect(store.hasActiveAlert('MSFT')).toBe(false);
  });

  it('creates, pauses and deletes, keeping local state in step', async () => {
    store.load();

    await firstValueFrom(
      store.create({ symbol: 'MSFT', direction: AlertDirection.Below, threshold: 400, mode: AlertMode.EveryQuote }),
    );
    expect(api['createPriceAlert']).toHaveBeenCalledWith(7, {
      symbol: 'MSFT',
      direction: AlertDirection.Below,
      threshold: 400,
      mode: AlertMode.EveryQuote,
    });
    expect(store.alerts().map((a) => a.symbol)).toEqual(['MSFT', 'AAPL']);

    await firstValueFrom(store.setActive(1, false));
    expect(store.alertsFor('AAPL')[0].active).toBe(false);
    expect(store.activeCount()).toBe(1);

    await firstValueFrom(store.delete(2));
    expect(store.alerts().map((a) => a.alertId)).toEqual([1]);
  });

  it('explains a server without the price-alert API instead of showing "Invalid input"', () => {
    api['getPriceAlerts'].mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'VAL-422', message: 'Invalid input' } })),
    );

    store.load();

    expect(store.error()).toContain('Price alerts are not available on the trading service yet');
  });

  it('surfaces the server message when the alert cap is reached', async () => {
    api['createPriceAlert'].mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { errorCode: 'ALR-409', message: 'Active price alert limit reached (20 per account); pause or delete one first' },
          }),
      ),
    );

    await expect(
      firstValueFrom(store.create({ symbol: 'AAPL', direction: AlertDirection.Above, threshold: 1, mode: AlertMode.Once })),
    ).rejects.toThrow('Active price alert limit reached');
  });
});
