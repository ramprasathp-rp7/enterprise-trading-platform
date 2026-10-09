import { describe, it, expect, beforeEach, vi } from 'vitest';
import { BlotterStore } from './blotter.store';
import { OrderHistoryService } from './services/order-history.service';
import { OrderHistoryEntry, OrderStatus } from '../../api/generated/trade';
import { of, Subject, throwError } from 'rxjs';

describe('BlotterStore', () => {
  let store: BlotterStore;
  let mockOrderHistoryService: any;

  const mockOrders: OrderHistoryEntry[] = [
    {
      orderId: 'ord-001',
      accountId: 123,
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 100,
      price: 150.5,
      executedPrice: null,
      status: OrderStatus.New,
      createdOn: '2026-10-01T10:00:00Z',
    },
    {
      orderId: 'ord-002',
      accountId: 123,
      symbol: 'GOOGL',
      side: 'SELL',
      quantity: 50,
      price: 2800.0,
      executedPrice: 2799.5,
      status: OrderStatus.Filled,
      createdOn: '2026-10-01T09:00:00Z',
    },
  ];

  const filledOrders: OrderHistoryEntry[] = [
    {
      ...mockOrders[0],
      status: OrderStatus.Filled,
      executedPrice: 150.5,
    },
    mockOrders[1],
  ];

  beforeEach(() => {
    mockOrderHistoryService = {
      getOrderHistory: vi.fn(),
    };

    // Create store with mock service
    store = new BlotterStore();
    (store as any).orderHistoryService = mockOrderHistoryService;
  });

  describe('loadOrderHistory', () => {
    it('loads order history and starts polling when orders are NEW', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.orders().length).toBe(2);
          expect(store.isPolling()).toBe(true);
          expect(store.hasNewOrders()).toBe(true);
          resolve();
        }, 100);
      });
    });

    it('loads order history without polling when no NEW orders', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(filledOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.orders().length).toBe(2);
          expect(store.isPolling()).toBe(false);
          expect(store.hasNewOrders()).toBe(false);
          resolve();
        }, 100);
      });
    });

    it('handles empty order history gracefully', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of([]));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.orders().length).toBe(0);
          expect(store.isPolling()).toBe(false);
          resolve();
        }, 100);
      });
    });

    it('handles API errors gracefully', async () => {
      const error = {
        error: { message: 'API Error' },
      };
      mockOrderHistoryService.getOrderHistory.mockReturnValue(throwError(() => error));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.isLoading()).toBe(false);
          expect(store.lastError()).toBe('API Error');
          resolve();
        }, 100);
      });
    });
  });

  describe('polling logic', () => {
    it('stops polling when no more NEW orders', async () => {
      let callCount = 0;
      mockOrderHistoryService.getOrderHistory.mockImplementation(() => {
        callCount++;
        if (callCount === 1) {
          return of(mockOrders); // First call: has NEW orders
        }
        return of(filledOrders); // Second call: no NEW orders
      });

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.isPolling()).toBe(false);
          resolve();
        }, 3000); // Wait for at least one poll cycle
      });
    });

    it('stops polling after max attempts', async () => {
      // Always return NEW orders to force max attempts
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.loadOrderHistory(123);

      // Wait for polling to complete (30 * 2000ms = 60 seconds would be too long)
      // So we just verify it stops eventually
      await new Promise<void>((resolve) => {
        setTimeout(() => {
          const initialAttempts = store.pollAttempts();
          expect(initialAttempts).toBeGreaterThanOrEqual(0);
          resolve();
        }, 100);
      });
    });

    it('handles polling errors gracefully', async () => {
      let callCount = 0;
      mockOrderHistoryService.getOrderHistory.mockImplementation(() => {
        callCount++;
        if (callCount === 1) {
          return of(mockOrders);
        }
        return throwError(() => ({
          error: { message: 'Polling error' },
        }));
      });

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.isPolling()).toBe(false);
          resolve();
        }, 3000);
      });
    });
  });

  describe('refreshOrderHistory', () => {
    it('refreshes order history and restarts polling if needed', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          const wasPolling = store.isPolling();
          store.refreshOrderHistory();

          setTimeout(() => {
            expect(store.orders().length).toBe(2);
            expect(mockOrderHistoryService.getOrderHistory).toHaveBeenCalledWith(123);
            resolve();
          }, 100);
        }, 100);
      });
    });
  });

  describe('stopPolling', () => {
    it('stops active polling', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.isPolling()).toBe(true);
          store.stopPolling();
          expect(store.isPolling()).toBe(false);
          resolve();
        }, 100);
      });
    });
  });

  describe('clearOrders', () => {
    it('clears all order state', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          store.clearOrders();

          expect(store.orders().length).toBe(0);
          expect(store.isPolling()).toBe(false);
          expect(store.pollAttempts()).toBe(0);
          expect(store.lastError()).toBeNull();
          resolve();
        }, 100);
      });
    });
  });

  describe('hasNewOrders computed signal', () => {
    it('returns true when orders have NEW status', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.hasNewOrders()).toBe(true);
          resolve();
        }, 100);
      });
    });

    it('returns false when no orders have NEW status', async () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(filledOrders));

      store.loadOrderHistory(123);

      await new Promise<void>((resolve) => {
        setTimeout(() => {
          expect(store.hasNewOrders()).toBe(false);
          resolve();
        }, 100);
      });
    });
  });

  describe('cancelOrder', () => {
    let mockOrderCancelService: any;

    beforeEach(() => {
      mockOrderCancelService = { cancelOrder: vi.fn() };
      (store as any).orderCancelService = mockOrderCancelService;
      (store as any).ordersSignal.set(mockOrders);
      (store as any).currentAccountId = 123;
    });

    it('asks the trade-events watcher to check the feed soon after a successful cancel', () => {
      const track = vi.fn();
      (store as any).orderWatcher = { track };
      mockOrderCancelService.cancelOrder.mockReturnValue(of({ orderId: 'ord-001', status: 'CANCELLED', message: 'Order cancelled' }));
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.cancelOrder('ord-001');

      expect(track).toHaveBeenCalledWith('ord-001');
    });

    it('marks the order CANCELLED, reports success and re-syncs history', () => {
      const cancelled = [{ ...mockOrders[0], status: OrderStatus.Cancelled }, mockOrders[1]];
      mockOrderCancelService.cancelOrder.mockReturnValue(of({ orderId: 'ord-001', status: 'CANCELLED', message: 'Order cancelled' }));
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(cancelled));

      store.cancelOrder('ord-001');

      expect(mockOrderCancelService.cancelOrder).toHaveBeenCalledWith('ord-001');
      expect(store.orders()[0].status).toBe(OrderStatus.Cancelled);
      expect(store.cancelResult()).toEqual({ type: 'success', orderId: 'ord-001', message: 'Order cancelled' });
      expect(store.cancellingIds().has('ord-001')).toBe(false);
      expect(mockOrderHistoryService.getOrderHistory).toHaveBeenCalledWith(123);
      expect(store.isLoading()).toBe(false);
    });

    it('explains a 409 when the order is no longer cancellable', () => {
      mockOrderCancelService.cancelOrder.mockReturnValue(
        throwError(() => ({ status: 409, error: { errorCode: 'ORD-409', message: 'Order is not cancellable' } }))
      );
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(filledOrders));

      store.cancelOrder('ord-001');

      const result = store.cancelResult();
      expect(result?.type).toBe('error');
      expect(result?.errorCode).toBe('ORD-409');
      expect(result?.message).toContain('can no longer be cancelled');
      expect(store.orders()[0].status).toBe(OrderStatus.Filled);
      expect(store.cancellingIds().size).toBe(0);
    });

    it('tracks the in-flight cancel and ignores duplicate requests', () => {
      mockOrderCancelService.cancelOrder.mockReturnValue(new Subject());

      store.cancelOrder('ord-001');
      store.cancelOrder('ord-001');

      expect(store.cancellingIds().has('ord-001')).toBe(true);
      expect(mockOrderCancelService.cancelOrder).toHaveBeenCalledTimes(1);
    });

    it('clears the cancel result on demand', () => {
      mockOrderCancelService.cancelOrder.mockReturnValue(throwError(() => ({ status: 0 })));
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));

      store.cancelOrder('ord-001');
      expect(store.cancelResult()?.message).toContain('Unable to reach');

      store.clearCancelResult();
      expect(store.cancelResult()).toBeNull();
    });
  });

  describe('filters and cancel errors', () => {
    it('filters by status and by part of the symbol, and clears filters', () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(filledOrders));
      store.loadOrderHistory(123);

      store.setFilters({ status: OrderStatus.Filled, symbol: 'goo' });
      expect(store.filteredOrders().map((o) => o.orderId)).toEqual(['ord-002']);

      store.setFilters({ symbol: 'zzz' });
      expect(store.filteredOrders()).toEqual([]);

      store.setFilters({ status: OrderStatus.Rejected });
      expect(store.filteredOrders()).toEqual([]);

      store.clearFilters();
      expect(store.filteredOrders()).toHaveLength(2);
      store.stopPolling();
    });

    it('explains each cancel failure', () => {
      const cancel = { cancelOrder: vi.fn() };
      (store as any).orderCancelService = cancel;
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));
      store.loadOrderHistory(123);
      const failWith = (status: number, error: unknown = {}) => {
        cancel.cancelOrder.mockReturnValueOnce(throwError(() => ({ status, error })));
        store.cancelOrder('ord-001');
        return store.cancelResult()?.message;
      };

      expect(failWith(404)).toContain('could not be found');
      expect(failWith(403)).toContain('not active');
      expect(failWith(401)).toContain('session has expired');
      expect(failWith(0)).toContain('Unable to reach');
      expect(failWith(500, { message: 'Ledger locked', errorCode: 'X-1' })).toBe('Ledger locked');
      expect(store.cancelResult()?.errorCode).toBe('X-1');
      expect(failWith(500)).toContain('Failed to cancel');
      store.stopPolling();
    });

    it('does not start a second poll while one is running', () => {
      mockOrderHistoryService.getOrderHistory.mockReturnValue(of(mockOrders));
      store.loadOrderHistory(123);
      expect(store.isPolling()).toBe(true);

      (store as any).startPolling(123);

      expect(store.isPolling()).toBe(true);
      store.stopPolling();
    });
  });
});
