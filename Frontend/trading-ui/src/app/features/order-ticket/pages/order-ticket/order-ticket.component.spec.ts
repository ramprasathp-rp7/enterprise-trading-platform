import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { OrderStatusWatcherService } from '../../../../core/notifications/order-status-watcher.service';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import { OrderTicketComponent } from './order-ticket.component';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { of } from 'rxjs';
import { CandleService } from '../../../instruments/services/candle.service';
import { QuoteIntraDayService } from '../../../instruments/services/quote-intraday.service';

describe('OrderTicketComponent', () => {
  let component: OrderTicketComponent;
  let fixture: ComponentFixture<OrderTicketComponent>;
  let store: {
    accountId: ReturnType<typeof signal<number | null>>;
    accountDisplayId: ReturnType<typeof signal<string | null>>;
    isSubmitting: ReturnType<typeof signal<boolean>>;
    submissionResult: ReturnType<typeof signal<any>>;
    errorCode: ReturnType<typeof signal<string | null>>;
    errorMessage: ReturnType<typeof signal<string | null>>;
    clearErrors: ReturnType<typeof vi.fn>;
    clearSubmissionResult: ReturnType<typeof vi.fn>;
    submitOrder: ReturnType<typeof vi.fn>;
    accountCurrency: ReturnType<typeof signal<string | null>>;
  };
  /** Live quotes by symbol; FX:USDINR = 80 makes one rupee USD 0.0125 */
  const fxQuotes: Record<string, { price: number }> = { 'FX:USDINR': { price: 80 } };

  beforeEach(async () => {
    store = {
      accountId: signal(101),
      accountDisplayId: signal('CUST-000101'),
      isSubmitting: signal(false),
      submissionResult: signal(null),
      errorCode: signal(null),
      errorMessage: signal(null),
      clearErrors: vi.fn(),
      clearSubmissionResult: vi.fn(),
      submitOrder: vi.fn().mockResolvedValue(true),
      accountCurrency: signal('USD'),
    };

    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideRouter([]),
        { provide: OrderTicketStore, useValue: store },
        {
          provide: LiveQuoteStore,
          useValue: {
            quote: (symbol: string) => fxQuotes[symbol] ?? null,
            entry: (symbol: string) => (fxQuotes[symbol] ? { status: 'ready', quote: fxQuotes[symbol], fetchedAt: 0 } : undefined),
            track: vi.fn(),
            refresh: vi.fn(),
          },
        },
        // The instrument panel's candles and intraday quotes
        { provide: CandleService, useValue: { getHistory: () => of([]) } },
        { provide: QuoteIntraDayService, useValue: { getQuotesForToday: () => of([]) } },
      ],
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
    expect(component.quantityErrors()[0]).toBe('Whole number above 0.');
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

  describe('after submission', () => {
    const fillValidOrder = () => {
      component.symbol.set('AAPL');
      component.side.set(OrderSide.Buy);
      component.quantity.set('10');
      component.price.set('245.75');
    };

    it('opens the confirmation page for the placed order', async () => {
      const router = TestBed.inject(Router);
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
      const track = vi.spyOn(TestBed.inject(OrderStatusWatcherService), 'track').mockImplementation(() => {});
      store.submitOrder.mockImplementation(async () => {
        store.submissionResult.set({
          orderId: 'ORD-123',
          status: 'NEW',
          message: 'Order accepted and queued.',
          symbol: 'AAPL',
          side: OrderSide.Buy,
          quantity: 10,
          price: 245.75,
        });
        return true;
      });
      fillValidOrder();

      await component.onSubmit();

      expect(store.submitOrder).toHaveBeenCalledWith({ symbol: 'AAPL', side: OrderSide.Buy, quantity: 10, price: 245.75 });
      expect(navigate).toHaveBeenCalledWith(['/order-confirmation', 'ORD-123']);
      expect(notify).toHaveBeenCalledWith({
        kind: 'order-created',
        title: 'Order received',
        message: "We've received your order to buy 10 AAPL at $245.75. We'll let you know as soon as it's completed.",
        link: { path: '/order-confirmation/ORD-123' },
        dedupeKey: 'order-created:ORD-123',
      });
      expect(track).toHaveBeenCalledWith('ORD-123', 'NEW');
    });

    it('stays on the ticket and shows the error when placement fails', async () => {
      const router = TestBed.inject(Router);
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
      store.submitOrder.mockImplementation(async () => {
        store.errorCode.set('ORD-409');
        store.errorMessage.set('Duplicate order');
        return false;
      });
      fillValidOrder();

      await component.onSubmit();
      fixture.detectChanges();

      expect(navigate).not.toHaveBeenCalled();
      expect(notify).not.toHaveBeenCalled();
      const rejected = fixture.nativeElement.querySelector('[data-testid="order-rejected-message"]');
      expect(rejected.textContent).toContain('Duplicate order');
    });

    it('no longer renders a success card on the ticket itself', () => {
      store.submissionResult.set({ orderId: 'ORD-1', status: 'NEW', message: 'ok', symbol: 'AAPL', side: OrderSide.Buy, quantity: 1, price: 1 });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="order-result-message"]')).toBeNull();
    });
  });

  describe('multi-currency estimate', () => {
    it('shows the notional in the instrument currency with an estimate in the account currency', () => {
      component.symbol.set('INFY.NS');
      component.quantity.set('40');
      component.price.set('1600');
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="summary-notional"]')!.textContent!.trim()).toBe('₹64,000.00');
      expect(component.estimatedFxRate()).toBeCloseTo(0.0125, 10);
      expect(el.querySelector('[data-testid="summary-notional-account"]')!.textContent!.trim()).toBe('≈ $800.00');
    });

    it('shows no estimate when the instrument trades in the account currency', () => {
      component.symbol.set('AAPL');
      component.quantity.set('2');
      component.price.set('100');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="summary-notional-account"]')).toBeNull();
    });
  });
});