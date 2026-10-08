import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ORDER_FEED_FAST_MS, ORDER_FEED_SLOW_MS, ORDER_NOTIFICATION_MAX_AGE_MS, OrderStatusWatcherService } from './order-status-watcher.service';
import { NotificationService } from './notification.service';
import { OrderNotificationResponse, OrderNotificationsService } from '../../api/generated/order-notifications';
import { CoreAuthService } from '../auth/core-auth.service';

describe('OrderStatusWatcherService (trade-events feed)', () => {
  let watcher: OrderStatusWatcherService;
  let api: { getOrderNotifications: ReturnType<typeof vi.fn> };
  let notifications: { notify: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const CURSOR_KEY = 'etp-order-notification-cursor:u-1:7';

  const item = (id: number, status: 'FILLED' | 'REJECTED' | 'CANCELLED', extra: Partial<OrderNotificationResponse> = {}): OrderNotificationResponse => ({
    id,
    orderId: `o${id}`,
    eventType: `ORDER_${status}` as OrderNotificationResponse.EventTypeEnum,
    status,
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 10,
    price: 189.35,
    executedPrice: status === 'FILLED' ? 189.3 : null,
    reason: status === 'REJECTED' ? 'Insufficient funds' : null,
    occurredAt: new Date().toISOString(),
    ...extra,
  });

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.removeItem(CURSOR_KEY);
    api = { getOrderNotifications: vi.fn(() => of([])) };
    notifications = { notify: vi.fn() };
    currentUser = vi.fn(() => ({ id: 'u-1', accountId: 7 }));
    TestBed.configureTestingModule({
      providers: [
        { provide: OrderNotificationsService, useValue: api },
        { provide: NotificationService, useValue: notifications },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    });
    watcher = TestBed.inject(OrderStatusWatcherService);
  });

  afterEach(() => {
    watcher.stop();
    localStorage.removeItem(CURSOR_KEY);
    vi.useRealTimers();
  });

  it('on first start only learns where the feed is, without replaying history into the bell', () => {
    api.getOrderNotifications.mockReturnValue(of([item(41, 'FILLED')]));

    watcher.start();
    vi.advanceTimersByTime(0); // first poll runs on the next tick

    expect(api.getOrderNotifications).toHaveBeenCalledWith(7, undefined, 1);
    expect(notifications.notify).not.toHaveBeenCalled();
    expect(localStorage.getItem(CURSOR_KEY)).toBe('41');
    expect(watcher.isPolling).toBe(true);
  });

  it('announces new fills and rejections from the feed, with the reason, then advances the cursor', () => {
    localStorage.setItem(CURSOR_KEY, '41');
    api.getOrderNotifications.mockReturnValue(of([item(42, 'FILLED'), item(43, 'REJECTED')]));

    watcher.start();
    vi.advanceTimersByTime(0); // first poll runs on the next tick

    expect(api.getOrderNotifications).toHaveBeenCalledWith(7, 41);
    expect(notifications.notify).toHaveBeenNthCalledWith(1, expect.objectContaining({
      kind: 'order-filled',
      title: 'Order completed',
      message: 'Good news! You bought 10 AAPL at $189.30.',
      link: { path: '/blotter' },
      dedupeKey: 'order-status:o42:FILLED',
    }));
    expect(notifications.notify).toHaveBeenNthCalledWith(2, expect.objectContaining({
      kind: 'order-rejected',
      message: expect.stringContaining('Reason: Insufficient funds.'),
      dedupeKey: 'order-status:o43:REJECTED',
    }));
    expect(localStorage.getItem(CURSOR_KEY)).toBe('43');
  });

  it('skips outcomes that happened long ago (e.g. a first-time backfill) but moves past them', () => {
    localStorage.setItem(CURSOR_KEY, '10');
    const old = new Date(Date.now() - ORDER_NOTIFICATION_MAX_AGE_MS - 60_000).toISOString();
    api.getOrderNotifications.mockReturnValue(of([item(11, 'FILLED', { occurredAt: old }), item(12, 'REJECTED')]));

    watcher.start();
    vi.advanceTimersByTime(0);

    expect(notifications.notify).toHaveBeenCalledTimes(1);
    expect(notifications.notify).toHaveBeenCalledWith(expect.objectContaining({ dedupeKey: 'order-status:o12:REJECTED' }));
    expect(localStorage.getItem(CURSOR_KEY)).toBe('12');
  });

  it('counts fills so views showing holdings can reload, but not rejections', () => {
    localStorage.setItem(CURSOR_KEY, '10');
    api.getOrderNotifications.mockReturnValue(of([item(11, 'FILLED'), item(12, 'REJECTED')]));

    expect(watcher.fills()).toBe(0);

    watcher.start();
    vi.advanceTimersByTime(0);

    expect(watcher.fills()).toBe(1);
  });

  it('starts again from the feed when it was reset below the saved cursor (e.g. table recreated)', () => {
    localStorage.setItem(CURSOR_KEY, '40');
    api.getOrderNotifications.mockImplementation((_id: number, after?: number, limit?: number) =>
      limit === 1 ? of([item(3, 'FILLED')]) : of([item(4, 'FILLED')]),
    );

    watcher.start();
    vi.advanceTimersByTime(0);

    // newest is 3 < 40: rebaseline to 3 without announcing
    expect(localStorage.getItem(CURSOR_KEY)).toBe('3');
    expect(notifications.notify).not.toHaveBeenCalled();

    watcher.track('o4', 'NEW');
    vi.advanceTimersByTime(ORDER_FEED_FAST_MS);
    expect(api.getOrderNotifications).toHaveBeenLastCalledWith(7, 3);
    expect(notifications.notify).toHaveBeenCalledWith(expect.objectContaining({ dedupeKey: 'order-status:o4:FILLED' }));
  });

  it('polls slowly by default and every few seconds after an order is placed here', () => {
    localStorage.setItem(CURSOR_KEY, '0');
    watcher.start();
    vi.advanceTimersByTime(0);
    // first poll of the session: check the cursor against the newest id, then read after it
    expect(api.getOrderNotifications).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(ORDER_FEED_FAST_MS * 2);
    expect(api.getOrderNotifications).toHaveBeenCalledTimes(2); // not due yet
    vi.advanceTimersByTime(ORDER_FEED_SLOW_MS);
    expect(api.getOrderNotifications).toHaveBeenCalledTimes(3);

    watcher.track('o99', 'NEW');
    vi.advanceTimersByTime(ORDER_FEED_FAST_MS);
    vi.advanceTimersByTime(ORDER_FEED_FAST_MS);
    expect(api.getOrderNotifications).toHaveBeenCalledTimes(5);
  });

  it('never announces an outcome just because an order was tracked', () => {
    localStorage.setItem(CURSOR_KEY, '0');
    watcher.track('o1', 'FILLED');

    expect(notifications.notify).not.toHaveBeenCalled();
    expect(watcher.isPolling).toBe(true);
  });

  it('keeps the cursor and retries on the next tick when the feed fails', () => {
    localStorage.setItem(CURSOR_KEY, '5');
    api.getOrderNotifications.mockReturnValueOnce(throwError(() => new Error('down')));
    watcher.track();

    api.getOrderNotifications.mockReturnValue(of([item(6, 'CANCELLED')]));
    vi.advanceTimersByTime(ORDER_FEED_FAST_MS);

    expect(api.getOrderNotifications).toHaveBeenLastCalledWith(7, 5);
    expect(notifications.notify).toHaveBeenCalledWith(expect.objectContaining({ kind: 'order-cancelled' }));
    expect(localStorage.getItem(CURSOR_KEY)).toBe('6');
  });

  it('does nothing without a signed-in account, and stops cleanly', () => {
    currentUser.mockReturnValue(null);
    watcher.start();
    expect(watcher.isPolling).toBe(false);
    expect(api.getOrderNotifications).not.toHaveBeenCalled();

    currentUser.mockReturnValue({ id: 'u-1', accountId: 7 });
    watcher.start();
    watcher.stop();
    expect(watcher.isPolling).toBe(false);
  });

  it("does not use another user's cursor for the same account", () => {
    localStorage.setItem('etp-order-notification-cursor:u-previous:7', '10');
    api.getOrderNotifications.mockReturnValue(of([item(12, 'FILLED')]));

    watcher.start();
    vi.advanceTimersByTime(0);

    // No cursor for this user yet: it only learns where the feed is and announces nothing.
    expect(api.getOrderNotifications).toHaveBeenCalledTimes(1);
    expect(api.getOrderNotifications).toHaveBeenCalledWith(7, undefined, 1);
    expect(notifications.notify).not.toHaveBeenCalled();
    expect(localStorage.getItem(CURSOR_KEY)).toBe('12');
    localStorage.removeItem('etp-order-notification-cursor:u-previous:7');
  });
});
