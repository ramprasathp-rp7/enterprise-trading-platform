import { describe, it, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { MarketDataService } from '../../../../api/generated/market-data';
import { CandleService } from '../../../instruments/services/candle.service';
import { QuoteIntraDayService } from '../../../instruments/services/quote-intraday.service';
import { OrderTicketStore } from '../../order-ticket.store';
import { AccountSettingsStore } from '../../../../core/settings/account-settings.store';
import { OrderTicketComponent } from './order-ticket.component';

/** Order budget warning and default quantity from the Preferences page */
describe('OrderTicketComponent – order budget and default quantity', () => {
  async function open(settings: { orderBudget: number | null; defaultQuantity: number | null }, queryParams: Record<string, string> = { symbol: 'AAPL' }) {
    const orderBudget = signal<number | null>(settings.orderBudget);
    const defaultQuantity = signal<number | null>(settings.defaultQuantity);
    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideRouter([]),
        { provide: CandleService, useValue: { getHistory: () => of([]) } },
        { provide: MarketDataService, useValue: { getLatestQuote: () => throwError(() => new HttpErrorResponse({ status: 404 })) } },
        { provide: QuoteIntraDayService, useValue: { getQuotesForToday: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) }, queryParamMap: new BehaviorSubject(convertToParamMap(queryParams)) },
        },
        { provide: AccountSettingsStore, useValue: { orderBudget, defaultQuantity, market: signal('ALL') } },
        {
          provide: OrderTicketStore,
          useValue: {
            accountId: signal(101),
            accountDisplayId: signal('CUST-000101'),
            accountCurrency: signal('USD'),
            isSubmitting: signal(false),
            submissionResult: signal(null),
            errorCode: signal(null),
            errorMessage: signal(null),
            clearErrors: vi.fn(),
            clearSubmissionResult: vi.fn(),
            submitOrder: vi.fn(),
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(OrderTicketComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, defaultQuantity };
  }

  const el = (fixture: { nativeElement: HTMLElement }, id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  it('warns when the order costs more than the budget, without blocking it', async () => {
    const { fixture } = await open({ orderBudget: 5000, defaultQuantity: null });
    const component = fixture.componentInstance;

    component.quantity.set('20');
    component.price.set('300');
    fixture.detectChanges();

    expect(component.overBudget()).toBe(true);
    expect(el(fixture, 'budget-warning')?.textContent).toContain('$6,000.00');
    expect(el(fixture, 'budget-warning')?.textContent).toContain('$5,000.00');
    const submit = el(fixture, 'submit-order-btn') as HTMLButtonElement;
    expect(submit.textContent).toContain('Submit anyway');
    expect(submit.disabled).toBe(false);
    expect(el(fixture, 'budget-settings-link')?.getAttribute('href')).toBe('/settings?section=trading');
  });

  it('does not warn at or under the budget, or without a budget', async () => {
    const { fixture } = await open({ orderBudget: 6000, defaultQuantity: null });
    const component = fixture.componentInstance;
    component.quantity.set('20');
    component.price.set('300');
    fixture.detectChanges();
    expect(component.overBudget()).toBe(false);
    expect(el(fixture, 'budget-warning')).toBeNull();

    TestBed.resetTestingModule();
    const second = await open({ orderBudget: null, defaultQuantity: null });
    second.fixture.componentInstance.quantity.set('1000');
    second.fixture.componentInstance.price.set('1000');
    second.fixture.detectChanges();
    expect(el(second.fixture, 'budget-warning')).toBeNull();
  });

  it('prefills the default quantity once, and never over a quantity being typed', async () => {
    const { fixture, defaultQuantity } = await open({ orderBudget: null, defaultQuantity: 10 });
    const component = fixture.componentInstance;
    expect(component.quantity()).toBe('10');

    component.quantity.set('3');
    component.quantityTouched.set(true);
    defaultQuantity.set(25);
    fixture.detectChanges();
    expect(component.quantity()).toBe('3');
  });
});
