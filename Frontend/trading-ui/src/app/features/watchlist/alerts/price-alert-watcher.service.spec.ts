import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AlertDirection, AlertMode, PriceAlertTriggerResponse, PriceAlertsService } from '../../../api/generated/watchlist';
import { CoreAuthService } from '../../../core/auth/core-auth.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { PriceAlertWatcherService } from './price-alert-watcher.service';
import { PriceAlertsStore } from './price-alerts.store';

const trigger = (overrides: Partial<PriceAlertTriggerResponse> = {}): PriceAlertTriggerResponse => ({
  triggerId: 1,
  alertId: 10,
  symbol: 'AAPL',
  direction: AlertDirection.Above,
  threshold: 190,
  mode: AlertMode.Once,
  price: 191.2,
  quoteAt: '2026-10-06T10:00:00Z',
  triggeredAt: '2026-10-06T10:00:01Z',
  reason: 'AAPL 191.2 >= 190 (ABOVE, ONCE)',
  deliveryStatus: PriceAlertTriggerResponse.DeliveryStatusEnum.Delivered,
  ...overrides,
});

describe('PriceAlertWatcherService', () => {
  let api: { getRecentPriceAlertTriggers: ReturnType<typeof vi.fn> };
  let notifications: { notify: ReturnType<typeof vi.fn> };
  let alertsStore: { isLoaded: () => boolean; load: ReturnType<typeof vi.fn> };
  let watcher: PriceAlertWatcherService;

  beforeEach(() => {
    localStorage.clear();
    api = { getRecentPriceAlertTriggers: vi.fn(() => of([])) };
    notifications = { notify: vi.fn((n) => ({ ...n, id: 'x' })) };
    alertsStore = { isLoaded: () => true, load: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: PriceAlertsService, useValue: api },
        { provide: NotificationService, useValue: notifications },
        { provide: PriceAlertsStore, useValue: alertsStore },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ id: 'u-1', accountId: 7 }) } },
      ],
    });
    watcher = TestBed.inject(PriceAlertWatcherService);
  });

  afterEach(() => watcher.stop());

  it('does not replay existing triggers on the very first start', () => {
    api.getRecentPriceAlertTriggers.mockReturnValue(of([trigger()]));

    watcher.start();
    watcher.poll(); // the timer's first tick is async; poll directly

    expect(api.getRecentPriceAlertTriggers).toHaveBeenCalledWith(7, undefined);
    expect(notifications.notify).not.toHaveBeenCalled();
    expect(localStorage.getItem('etp-price-alert-cursor:u-1:7')).toBe('2026-10-06T10:00:01Z');
  });

  it('puts each newly delivered trigger into the bell as a price-alert, then asks for newer ones only', () => {
    watcher.start();
    watcher.poll(); // baseline: nothing yet
    api.getRecentPriceAlertTriggers.mockReturnValue(
      of([
        trigger({ triggerId: 2, symbol: 'MSFT', direction: AlertDirection.Below, threshold: 400, price: 399.5,
          mode: AlertMode.EveryQuote, triggeredAt: '2026-10-06T10:05:00Z' }),
        trigger(),
      ]),
    );

    watcher.poll();

    expect(notifications.notify).toHaveBeenCalledTimes(2);
    expect(notifications.notify).toHaveBeenNthCalledWith(1, {
      kind: 'price-alert',
      title: 'Price alert: AAPL',
      message: 'AAPL is at $191.20, above your $190.00 level. This alert is now off.',
      link: { path: '/watchlists' },
      dedupeKey: 'price-alert:1',
    });
    expect(notifications.notify.mock.calls[1][0]).toMatchObject({
      message: 'MSFT is at $399.50, below your $400.00 level.',
      dedupeKey: 'price-alert:2',
    });
    expect(alertsStore.load).toHaveBeenCalled();

    api.getRecentPriceAlertTriggers.mockReturnValue(of([]));
    watcher.poll();
    expect(api.getRecentPriceAlertTriggers).toHaveBeenLastCalledWith(7, '2026-10-06T10:05:00Z');
  });

  it('skips failed triggers and never moves the cursor past a pending one', () => {
    watcher.start();
    watcher.poll(); // baseline: nothing yet
    const { Failed, Pending } = PriceAlertTriggerResponse.DeliveryStatusEnum;
    api.getRecentPriceAlertTriggers.mockReturnValue(
      of([
        trigger({ triggerId: 3, triggeredAt: '2026-10-06T10:09:00Z' }),
        trigger({ triggerId: 2, deliveryStatus: Pending, triggeredAt: '2026-10-06T10:08:00Z' }),
        trigger({ triggerId: 1, deliveryStatus: Failed, triggeredAt: '2026-10-06T10:07:00Z' }),
      ]),
    );

    watcher.poll();

    expect(notifications.notify).not.toHaveBeenCalled();
    expect(localStorage.getItem('etp-price-alert-cursor:u-1:7')).toBe('2026-10-06T10:07:00Z');
  });
});
