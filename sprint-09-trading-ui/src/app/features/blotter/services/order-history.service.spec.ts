import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { OrderHistoryService } from './order-history.service';
import { AccountsService } from '../../../api/generated/trade/api/accounts.service';
import { OrderHistoryEntry, OrderStatus } from '../../../api/generated/trade';
import { of } from 'rxjs';

describe('OrderHistoryService', () => {
  let service: OrderHistoryService;
  let mockAccountsApi: any;

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

  beforeEach(() => {
    mockAccountsApi = {
      getOrders: vi.fn().mockReturnValue(of(mockOrders)),
    };

    TestBed.configureTestingModule({
      providers: [
        OrderHistoryService,
        { provide: AccountsService, useValue: mockAccountsApi },
      ],
    });

    service = TestBed.inject(OrderHistoryService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getOrderHistory', () => {
    it('calls AccountsService.getOrders with account ID', async () => {
      await new Promise<void>((resolve) => {
        service.getOrderHistory(123).subscribe(() => {
          expect(mockAccountsApi.getOrders).toHaveBeenCalledWith(123);
          resolve();
        });
      });
    });

    it('returns order history from API', async () => {
      await new Promise<void>((resolve) => {
        service.getOrderHistory(123).subscribe((orders) => {
          expect(orders).toEqual(mockOrders);
          expect(orders.length).toBe(2);
          resolve();
        });
      });
    });

    it('returns empty array when no orders', async () => {
      mockAccountsApi.getOrders.mockReturnValue(of([]));

      await new Promise<void>((resolve) => {
        service.getOrderHistory(123).subscribe((orders) => {
          expect(orders).toEqual([]);
          resolve();
        });
      });
    });

    it('includes all required order fields', async () => {
      await new Promise<void>((resolve) => {
        service.getOrderHistory(123).subscribe((orders) => {
          const order = orders[0];
          expect(order.orderId).toBeDefined();
          expect(order.accountId).toBeDefined();
          expect(order.symbol).toBeDefined();
          expect(order.side).toBeDefined();
          expect(order.quantity).toBeDefined();
          expect(order.price).toBeDefined();
          expect(order.status).toBeDefined();
          expect(order.createdOn).toBeDefined();
          resolve();
        });
      });
    });

    it('handles different account IDs', async () => {
      await new Promise<void>((resolve) => {
        service.getOrderHistory(456).subscribe(() => {
          expect(mockAccountsApi.getOrders).toHaveBeenCalledWith(456);
          resolve();
        });
      });
    });
  });
});
