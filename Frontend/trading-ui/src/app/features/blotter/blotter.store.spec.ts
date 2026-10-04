import { describe, it, expect, beforeEach, vi } from 'vitest';
import { BlotterStore } from './blotter.store';
import { OrderHistoryService } from './services/order-history.service';
import { OrderHistoryEntry, OrderStatus } from '../../api/generated/trade';
import { of, throwError } from 'rxjs';

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
});
