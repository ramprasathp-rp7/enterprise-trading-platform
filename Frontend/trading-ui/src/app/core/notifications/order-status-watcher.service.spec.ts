import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OrderStatusWatcherService } from './order-status-watcher.service';
import { NotificationService } from './notification.service';
import { AccountsService, OrderStatus } from '../../api/generated/trade';
import { CoreAuthService } from '../auth/core-auth.service';

describe('OrderStatusWatcherService', () => {
  let watcher: OrderStatusWatcherService;
  let accounts: { getOrders: ReturnType<typeof vi.fn> };
  let notifications: { notify: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const order = (id: string, status: string, extra: object = {}) => ({
    orderId: id, accountId: 7, symbol: 'AAPL', side: 'BUY', quantity: 10, price: 189.35, executedPrice: null, status, createdOn: '', ...extra,
  });

  beforeEach(() => {
    vi.useFakeTimers();
    accounts = { getOrders: vi.fn(() => of([order('o1', 'NEW'), order('o2', 'FILLED')])) };
    notifications = { notify: vi.fn() };
    currentUser = vi.fn(() => ({ accountId: 7 }));
    TestBed.configureTestingModule({
      providers: [
        { provide: AccountsService, useValue: accounts },
        { provide: NotificationService, useValue: notifications },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    });
    watcher = TestBed.inject(OrderStatusWatcherService);
  });

  afterEach(() => {
    watcher.stop();
    vi.useRealTimers();
  });

  it('learns existing statuses on start without notifying, and polls while an order is NEW', () => {
    watcher.start();

    expect(accounts.getOrders).toHaveBeenCalledTimes(1);
    expect(notifications.notify).not.toHaveBeenCalled();
    expect(watcher.isPolling).toBe(true);
  });

  it('notifies when a NEW order fills, then stops polling once nothing is pending', () => {
    watcher.start();
    accounts.getOrders.mockReturnValue(of([order('o1', 'FILLED', { executedPrice: 189.3 }), order('o2', 'FILLED')]));

    vi.advanceTimersByTime(5000);

    expect(notifications.notify).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'order-filled',
      title: 'Order completed',
      message: 'Good news! You bought 10 AAPL at 189.30.',
      link: { path: '/blotter' },
      dedupeKey: 'order-status:o1:FILLED',
    }));
    expect(watcher.isPolling).toBe(false);
  });

  it('notifies rejections and cancellations', () => {
    accounts.getOrders.mockReturnValue(of([order('a', 'NEW'), order('b', 'NEW')]));
    watcher.start();
    accounts.getOrders.mockReturnValue(of([order('a', 'REJECTED'), order('b', 'CANCELLED')]));

    vi.advanceTimersByTime(5000);

    const kinds = notifications.notify.mock.calls.map((c) => c[0].kind);
    expect(kinds).toEqual(['order-rejected', 'order-cancelled']);
    expect(notifications.notify.mock.calls[0][0]).toMatchObject({
      title: "Order couldn't be completed",
      message: 'Your order to buy 10 AAPL at 189.35 was rejected. Open your order blotter for details.',
    });
    expect(notifications.notify.mock.calls[1][0].message).toBe('Your order to buy 10 AAPL at 189.35 has been cancelled.');
  });

  it('does not poll when nothing is NEW, but starts when a new order is tracked', () => {
    accounts.getOrders.mockReturnValue(of([order('o2', 'FILLED')]));
    watcher.start();
    expect(watcher.isPolling).toBe(false);

    watcher.track('o9', OrderStatus.New);
    expect(watcher.isPolling).toBe(true);

    accounts.getOrders.mockReturnValue(of([order('o9', 'FILLED')]));
    vi.advanceTimersByTime(5000);
    expect(notifications.notify).toHaveBeenCalledTimes(1);
  });

  it('stops after 10 idle minutes even if an order stays NEW', () => {
    watcher.start();

    vi.advanceTimersByTime(10 * 60_000 + 5000);

    expect(watcher.isPolling).toBe(false);
  });

  it('starts only once, needs an account, and survives request errors', () => {
    currentUser.mockReturnValue(null);
    watcher.start();
    expect(accounts.getOrders).not.toHaveBeenCalled();

    currentUser.mockReturnValue({ accountId: 7 });
    watcher.start();
    watcher.start();
    expect(accounts.getOrders).toHaveBeenCalledTimes(1);

    accounts.getOrders.mockReturnValue(throwError(() => new Error('down')));
    vi.advanceTimersByTime(5000);
    expect(watcher.isPolling).toBe(true);
    expect(notifications.notify).not.toHaveBeenCalled();
  });

  it('stays idle when the initial load fails', () => {
    accounts.getOrders.mockReturnValue(throwError(() => new Error('down')));

    watcher.start();

    expect(watcher.isPolling).toBe(false);
  });
});
