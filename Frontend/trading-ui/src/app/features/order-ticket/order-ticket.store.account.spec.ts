import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AccountsService, OrderSide } from '../../api/generated/trade';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { OrderTicketStore } from './order-ticket.store';
import { OrderTicketService } from './services/order-ticket.service';

/**
 * Account resolution and state-reset behaviour of the order ticket store.
 * (Order submission outcomes are covered in order-ticket.store.spec.ts.)
 */
describe('OrderTicketStore – account resolution', () => {
  let orderTicketService: { placeOrder: ReturnType<typeof vi.fn> };
  let accountsService: { getAccount: ReturnType<typeof vi.fn>; getBalance: ReturnType<typeof vi.fn> };
  let authService: {
    currentUser: ReturnType<typeof vi.fn>;
    accessToken: ReturnType<typeof vi.fn>;
    decodeJWT: ReturnType<typeof vi.fn>;
  };

  const account = { id: 101, accountId: 'CUST-000101', holderName: 'Priya', cashBalance: 0, status: 'ACTIVE', version: 1, lastUpdated: '' };
  const order = { symbol: 'AAPL', side: OrderSide.Buy, quantity: 1, price: 10 };

  const create = () => {
    TestBed.configureTestingModule({
      providers: [
        OrderTicketStore,
        { provide: OrderTicketService, useValue: orderTicketService },
        { provide: CoreAuthService, useValue: authService },
        { provide: AccountsService, useValue: accountsService },
      ],
    });
    const store = TestBed.inject(OrderTicketStore);
    TestBed.tick();
    return store;
  };

  beforeEach(() => {
    orderTicketService = { placeOrder: vi.fn() };
    accountsService = {
      getBalance: vi.fn(() => of({ accountId: 101, cashBalance: 1000, currency: 'USD', asOf: '' })),
      getAccount: vi.fn(() => of(account)),
    };
    authService = {
      currentUser: vi.fn(() => null),
      accessToken: vi.fn(() => null),
      decodeJWT: vi.fn(),
    };
  });

  it('uses the signed-in user and looks up the display account id', () => {
    authService.currentUser.mockReturnValue({ accountId: 101 });

    const store = create();

    expect(store.accountId()).toBe(101);
    expect(accountsService.getAccount).toHaveBeenCalledWith(101);
    expect(store.accountDisplayId()).toBe('CUST-000101');
  });

  it("takes the account's currency from its balance", () => {
    authService.currentUser.mockReturnValue({ accountId: 101 });

    const store = create();

    expect(accountsService.getBalance).toHaveBeenCalledWith(101);
    expect(store.accountCurrency()).toBe('USD');
  });

  it('falls back to the account id inside the access token', () => {
    authService.accessToken.mockReturnValue('jwt');
    authService.decodeJWT.mockReturnValue({ accountId: 202 });

    const store = create();

    expect(store.accountId()).toBe(202);
    expect(accountsService.getAccount).toHaveBeenCalledWith(202);
  });

  it('has no account when the token cannot be decoded', () => {
    authService.accessToken.mockReturnValue('jwt');
    authService.decodeJWT.mockImplementation(() => {
      throw new Error('bad token');
    });

    const store = create();

    expect(store.accountId()).toBeNull();
    expect(store.accountDisplayId()).toBeNull();
    expect(accountsService.getAccount).not.toHaveBeenCalled();
  });

  it('clears the display id when the account lookup fails', () => {
    authService.currentUser.mockReturnValue({ accountId: 101 });
    accountsService.getAccount.mockReturnValue(throwError(() => new Error('down')));

    const store = create();

    expect(store.accountDisplayId()).toBeNull();
  });

  it('treats an empty display id as none', () => {
    authService.currentUser.mockReturnValue({ accountId: 101 });
    accountsService.getAccount.mockReturnValue(of({ ...account, accountId: '' }));

    expect(create().accountDisplayId()).toBeNull();
  });

  it('refuses to submit without an account and reports AUTH-401', async () => {
    const store = create();

    const ok = await store.submitOrder(order);

    expect(ok).toBe(false);
    expect(orderTicketService.placeOrder).not.toHaveBeenCalled();
    expect(store.errorCode()).toBe('AUTH-401');
    expect(store.errorMessage()).toContain('session has expired');
  });

  it('clearErrors and clearSubmissionResult reset their state', async () => {
    authService.currentUser.mockReturnValue({ accountId: 101 });
    const store = create();

    orderTicketService.placeOrder.mockReturnValue(of({ orderId: 'ORD-1', status: 'NEW', message: 'ok' }));
    await store.submitOrder(order);
    expect(store.submissionResult()).not.toBeNull();
    store.clearSubmissionResult();
    expect(store.submissionResult()).toBeNull();

    orderTicketService.placeOrder.mockReturnValue(throwError(() => ({ status: 409, error: { errorCode: 'ORD-409', message: 'dup' } })));
    await store.submitOrder(order);
    expect(store.errorCode()).not.toBeNull();
    store.clearErrors();
    expect(store.errorCode()).toBeNull();
    expect(store.errorMessage()).toBeNull();
  });
});
