import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import { OrderTicketComponent } from './order-ticket.component';

describe('OrderTicketComponent', () => {
  let component: OrderTicketComponent;
  let fixture: ComponentFixture<OrderTicketComponent>;
  let store: {
    accountId: ReturnType<typeof signal<number | null>>;
    isSubmitting: ReturnType<typeof signal<boolean>>;
    submissionResult: ReturnType<typeof signal<any>>;
    errorCode: ReturnType<typeof signal<string | null>>;
    errorMessage: ReturnType<typeof signal<string | null>>;
    clearErrors: ReturnType<typeof vi.fn>;
    submitOrder: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    store = {
      accountId: signal(101),
      isSubmitting: signal(false),
      submissionResult: signal(null),
      errorCode: signal(null),
      errorMessage: signal(null),
      clearErrors: vi.fn(),
      submitOrder: vi.fn().mockResolvedValue(true),
    };

    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [{ provide: OrderTicketStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTicketComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('blocks an invalid quantity before submission', async () => {
    component.symbol.set('AAPL');
    component.side.set(OrderSide.Buy);
    component.quantity.set('2.5');
    component.price.set('245.75');
    fixture.detectChanges();

    await component.onSubmit();

    expect(store.submitOrder).not.toHaveBeenCalled();
    expect(component.quantityErrors()[0]).toContain('whole number greater than zero');
  });

  it('hides validation errors until a field is touched or submit is attempted', () => {
    expect(component.symbolErrors()).toEqual([]);
    expect(component.quantityErrors()).toEqual([]);
    expect(component.priceErrors()).toEqual([]);

    component.onQuantityBlur();
    fixture.detectChanges();

    expect(component.quantityErrors()).toContain('Quantity is required.');
    expect(component.symbolErrors()).toEqual([]);
    expect(component.priceErrors()).toEqual([]);
  });

  it('marks all fields touched when submit is attempted with empty values', async () => {
    await component.onSubmit();

    expect(component.symbolErrors()).toContain('Symbol is required.');
    expect(component.quantityErrors()).toContain('Quantity is required.');
    expect(component.priceErrors()).toContain('Price is required.');
    expect(store.submitOrder).not.toHaveBeenCalled();
  });

  it('renders the returned status from a submitted order', () => {
    store.submissionResult.set({
      orderId: 'ORD-123',
      status: 'NEW',
      message: 'Order accepted and queued.',
      symbol: 'AAPL',
      side: OrderSide.Buy,
      quantity: 10,
      price: 245.75,
    });
    fixture.detectChanges();

    const result = fixture.nativeElement.querySelector('[data-testid="order-result"]');
    expect(result).toBeTruthy();
    expect(result.textContent).toContain('NEW');
    expect(result.textContent).toContain('Order accepted and queued.');
  });
});