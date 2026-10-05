import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderConfirmationComponent } from './order-confirmation.component';
import { OrderTicketStore } from '../../order-ticket.store';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { AccountsService, OrderSide } from '../../../../api/generated/trade';

describe('OrderConfirmationComponent', () => {
  let fixture: ComponentFixture<OrderConfirmationComponent>;
  let component: OrderConfirmationComponent;
  let submissionResult: ReturnType<typeof signal<any>>;
  let accounts: { getOrders: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const placed = {
    orderId: 'ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e',
    status: 'NEW',
    message: 'Order accepted and queued.',
    symbol: 'AAPL',
    side: OrderSide.Buy,
    quantity: 10,
    price: 245.75,
  };

  async function create(routeOrderId: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OrderConfirmationComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(routeOrderId ? { orderId: routeOrderId } : {}) } } },
        { provide: OrderTicketStore, useValue: { submissionResult: submissionResult } },
        { provide: CoreAuthService, useValue: { currentUser } },
        { provide: AccountsService, useValue: accounts },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OrderConfirmationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const text = (selector: string) => fixture.nativeElement.querySelector(selector)?.textContent ?? '';

  beforeEach(() => {
    submissionResult = signal<any>(null);
    accounts = { getOrders: vi.fn(() => of([])) };
    currentUser = vi.fn(() => ({ accountId: 7 }));
  });

  it('shows the just-placed order without calling the API', async () => {
    submissionResult.set(placed);

    await create(placed.orderId);

    expect(accounts.getOrders).not.toHaveBeenCalled();
    expect(text('[data-testid="order-result-message"]')).toContain('Order accepted and working');
    expect(text('[data-testid="order-result-message"]')).toContain('Order accepted and queued.');
    expect(text('[data-testid="order-status"]')).toContain('NEW');
    expect(text('[data-testid="order-id"]')).toBe(placed.orderId);
    expect(component.notional()).toBeCloseTo(2457.5);
    expect(fixture.nativeElement.querySelector('.status-hint')).not.toBeNull();
  });

  it('links to the Order Blotter and back to the ticket', async () => {
    submissionResult.set(placed);

    await create(placed.orderId);

    expect(fixture.nativeElement.querySelector('[data-testid="view-in-blotter-btn"]').getAttribute('href')).toBe('/blotter');
    expect(fixture.nativeElement.querySelector('[data-testid="place-another-btn"]').getAttribute('href')).toBe('/order-ticket');
  });

  it('matches the stored result even when the route id has no ORD- prefix', async () => {
    submissionResult.set(placed);

    await create('6F2B1C2A-6A1E-4A4F-9C0D-2F7A1B3C4D5E');

    expect(component.order()?.symbol).toBe('AAPL');
    expect(accounts.getOrders).not.toHaveBeenCalled();
  });

  it('styles a synchronously rejected order as rejected', async () => {
    submissionResult.set({ ...placed, status: 'REJECTED', message: 'Insufficient funds' });

    await create(placed.orderId);

    expect(component.isRejected()).toBe(true);
    expect(text('.hero-title')).toBe('Order rejected');
    expect(fixture.nativeElement.querySelector('.confirmation-card').classList).toContain('is-rejected');
    expect(fixture.nativeElement.querySelector('.status-hint')).toBeNull();
  });

  it('looks the order up in the account history after a reload', async () => {
    accounts.getOrders.mockReturnValue(
      of([
        { orderId: 'ORD-other', accountId: 7, symbol: 'MSFT', side: 'SELL', quantity: 1, price: 1, status: 'NEW', createdOn: '' },
        { orderId: placed.orderId, accountId: 7, symbol: 'AAPL', side: 'BUY', quantity: 10, price: 245.75, status: 'FILLED', createdOn: '' },
      ])
    );

    await create(placed.orderId);

    expect(accounts.getOrders).toHaveBeenCalledWith(7);
    expect(component.loading()).toBe(false);
    expect(text('.hero-title')).toBe('Order filled');
    expect(text('[data-testid="order-status"]')).toContain('FILLED');
  });

  it('ignores a stored result for a different order', async () => {
    submissionResult.set({ ...placed, orderId: 'ORD-something-else' });

    await create(placed.orderId);

    expect(accounts.getOrders).toHaveBeenCalled();
  });

  it('shows a not-found state with a blotter link when the order is unknown', async () => {
    await create('ORD-missing');

    expect(component.notFound()).toBe(true);
    expect(text('[data-testid="order-not-found"]')).toContain("We couldn't find this order");
    expect(fixture.nativeElement.querySelector('[data-testid="view-in-blotter-btn"]').getAttribute('href')).toBe('/blotter');
  });

  it('shows not-found when the history lookup fails', async () => {
    accounts.getOrders.mockReturnValue(throwError(() => new Error('down')));

    await create(placed.orderId);

    expect(component.notFound()).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('does not call the API without an order id or a signed-in account', async () => {
    currentUser.mockReturnValue(null);

    await create(placed.orderId);

    expect(accounts.getOrders).not.toHaveBeenCalled();
    expect(component.notFound()).toBe(true);
  });

  it('titles filled, cancelled and unknown statuses', async () => {
    submissionResult.set(placed);
    await create(placed.orderId);

    component.order.set({ ...placed, status: 'FILLED' });
    expect(component.title()).toBe('Order filled');
    component.order.set({ ...placed, status: 'CANCELLED' });
    expect(component.title()).toBe('Order cancelled');
    component.order.set({ ...placed, status: 'SOMETHING' });
    expect(component.title()).toBe('Order placed');
    component.order.set(null);
    expect(component.notional()).toBeNull();
  });
});
