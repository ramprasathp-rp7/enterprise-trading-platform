import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AccountsService } from '../../api/generated/trade';
import { OrderSide } from '../../api/generated/trade';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { OrderTicketStore } from './order-ticket.store';
import { OrderTicketService } from './services/order-ticket.service';

describe('OrderTicketStore', () => {
  let store: OrderTicketStore;
  let orderTicketService: { placeOrder: ReturnType<typeof vi.fn> };
  let accountsService: { getAccount: ReturnType<typeof vi.fn>; getBalance: ReturnType<typeof vi.fn> };
  let authService: {
    currentUser: ReturnType<typeof vi.fn>;
    accessToken: ReturnType<typeof vi.fn>;
    decodeJWT: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    orderTicketService = {
      placeOrder: vi.fn(),
    };

    accountsService = {
      getBalance: vi.fn(() => of({ accountId: 101, cashBalance: 1000, currency: 'USD', asOf: '' })),
      getAccount: vi.fn(() =>
        of({
          id: 101,
          accountId: 'CUST-000101',
          holderName: 'Priya Menon',
          cashBalance: 0,
          status: 'ACTIVE',
          version: 1,
          lastUpdated: new Date().toISOString(),
        })
      ),
    };

    authService = {
      currentUser: vi.fn(() => ({ id: 'user-1', username: 'priya.menon', accountId: 101, roles: ['CUSTOMER'] })),
      accessToken: vi.fn(() => null),
      decodeJWT: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        OrderTicketStore,
        { provide: OrderTicketService, useValue: orderTicketService },
        { provide: CoreAuthService, useValue: authService },
        { provide: AccountsService, useValue: accountsService },
      ],
    });

    store = TestBed.inject(OrderTicketStore);
  });

  it('submits a valid order and shows the returned status', async () => {
    orderTicketService.placeOrder.mockReturnValue(
      of({
        orderId: 'ORD-123',
        status: 'NEW',
        message: 'Order accepted and queued.',
        symbol: 'AAPL',
        side: OrderSide.Buy,
        quantity: 10,
        price: 245.75,
      })
    );

    const success = await store.submitOrder({
      symbol: 'AAPL',
      side: OrderSide.Buy,
      quantity: 10,
      price: 245.75,
    });

    expect(success).toBe(true);
    expect(orderTicketService.placeOrder).toHaveBeenCalledWith(101, {
      symbol: 'AAPL',
      side: OrderSide.Buy,
      quantity: 10,
      price: 245.75,
    });
    expect(store.submissionResult()?.status).toBe('NEW');
    expect(store.errorMessage()).toBeNull();
  });

  it('shows the mapped message for a business-rule rejection', async () => {
    orderTicketService.placeOrder.mockReturnValue(
      throwError(() => ({ error: { errorCode: 'ORD-400', message: 'Insufficient funds' } }))
    );

    const success = await store.submitOrder({
      symbol: 'AAPL',
      side: OrderSide.Buy,
      quantity: 10,
      price: 245.75,
    });

    expect(success).toBe(false);
    expect(store.submissionResult()).toBeNull();
    expect(store.errorCode()).toBe('ORD-400');
    expect(store.errorMessage()).toBe('There is not enough cash for this order. Please deposit funds or reduce the order size.');
  });
});