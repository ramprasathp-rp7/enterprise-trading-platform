import { describe, it, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { MarketDataService } from '../../../../api/generated/market-data';
import { CandleService } from '../../../instruments/services/candle.service';
import { QuoteIntraDayService } from '../../../instruments/services/quote-intraday.service';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import { OrderTicketComponent } from './order-ticket.component';

/** Prefilling the ticket from query params (Positions → Buy / Sell, instrument search). */
describe('OrderTicketComponent – prefill from a holding', () => {
  let params$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  async function open(queryParams: Record<string, string>) {
    params$ = new BehaviorSubject(convertToParamMap(queryParams));
    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideRouter([]),
        // Market data for the instrument panel: two daily candles, no cached quote yet
        {
          provide: CandleService,
          useValue: {
            getHistory: () => of([
              { date: '2026-10-01', open: 1, high: 2, low: 1, close: 2, adjclose: 2, volume: 10, synthetic: false },
              { date: '2026-10-02', open: 2, high: 3, low: 2, close: 3, adjclose: 3, volume: 10, synthetic: false },
            ]),
          },
        },
        { provide: MarketDataService, useValue: { getLatestQuote: () => throwError(() => new HttpErrorResponse({ status: 404 })) } },
        { provide: QuoteIntraDayService, useValue: { getQuotesForToday: () => of([]) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) }, queryParamMap: params$ } },
        {
          provide: OrderTicketStore,
          useValue: {
            accountId: signal(101),
            accountDisplayId: signal('CUST-000101'),
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
    return fixture;
  }

  it('fills the instrument and selects SELL', async () => {
    const fixture = await open({ symbol: 'aapl', side: 'sell', from: 'holding' });
    const component = fixture.componentInstance;

    expect(component.symbol()).toBe('AAPL');
    expect(component.side()).toBe(OrderSide.Sell);
    expect(component.prefilledFrom()).toBe('AAPL');
    expect((fixture.nativeElement.querySelector('[data-testid="symbol-input"]') as HTMLInputElement).value).toBe('AAPL');
    expect(fixture.nativeElement.querySelector('[data-testid="side-sell"]').classList).toContain('active');
    expect(fixture.nativeElement.querySelector('[data-testid="prefill-hint"]').textContent).toContain('AAPL holding');
  });

  it('fills the instrument and keeps BUY', async () => {
    const fixture = await open({ symbol: 'INFY.NS', side: 'BUY' });

    expect(fixture.componentInstance.symbol()).toBe('INFY.NS');
    expect(fixture.componentInstance.side()).toBe(OrderSide.Buy);
  });

  it('ignores an unknown side and trims an over-long symbol to the field limit', async () => {
    const fixture = await open({ symbol: '  abcdefghijklmnopqrstuvwxyz  ', side: 'short' });

    expect(fixture.componentInstance.side()).toBe(OrderSide.Buy);
    expect(fixture.componentInstance.symbol()).toBe('ABCDEFGHIJKLMNOPQRST');
  });

  it('shows validation instead of the hint for a symbol that is not tradeable', async () => {
    const fixture = await open({ symbol: 'bad symbol!', from: 'holding' });

    expect(fixture.componentInstance.symbolErrors().length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('[data-testid="prefill-hint"]')).toBeNull();
  });

  it('leaves the ticket empty without query params', async () => {
    const fixture = await open({});

    expect(fixture.componentInstance.symbol()).toBe('');
    expect(fixture.componentInstance.prefilledFrom()).toBeNull();
    expect(fixture.componentInstance.side()).toBe(OrderSide.Buy);
  });

  describe('prefill hint', () => {
    const type = async (fixture: any, value: string) => {
      const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="symbol-input"]');
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    };
    const hint = (fixture: any) => fixture.nativeElement.querySelector('[data-testid="prefill-hint"]');

    it('disappears when the instrument field is erased', async () => {
      const fixture = await open({ symbol: 'MSFT', side: 'SELL', from: 'holding' });
      expect(hint(fixture)).not.toBeNull();

      await type(fixture, '');

      expect(fixture.componentInstance.prefilledFrom()).toBeNull();
      expect(hint(fixture)).toBeNull();
    });

    it('stays gone when the same symbol is typed back in', async () => {
      const fixture = await open({ symbol: 'MSFT', from: 'holding' });

      await type(fixture, '');
      await type(fixture, 'MSFT');

      expect(hint(fixture)).toBeNull();
    });

    it('disappears when a different symbol is typed', async () => {
      const fixture = await open({ symbol: 'MSFT', from: 'holding' });

      await type(fixture, 'AAPL');

      expect(hint(fixture)).toBeNull();
    });

    it('stays when only the letter case changes', async () => {
      const fixture = await open({ symbol: 'MSFT', from: 'holding' });

      await type(fixture, 'msft');

      expect(fixture.componentInstance.prefilledFrom()).toBe('MSFT');
      expect(hint(fixture)).not.toBeNull();
    });
  });

  describe('instrument search', () => {
    it('shows no holding hint when opened from search (no from=holding)', async () => {
      const fixture = await open({ symbol: 'INFY.NS' });

      expect(fixture.componentInstance.symbol()).toBe('INFY.NS');
      expect(fixture.componentInstance.prefilledFrom()).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="prefill-hint"]')).toBeNull();
    });

    it('follows later query-param changes (navbar search while already on the page)', async () => {
      const fixture = await open({ symbol: 'AAPL' });

      params$.next(convertToParamMap({ symbol: 'X:BTC-USD' }));
      fixture.detectChanges();

      expect(fixture.componentInstance.symbol()).toBe('X:BTC-USD');
      expect(fixture.nativeElement.querySelector('[data-testid="instrument-symbol"]').textContent).toContain('X:BTC-USD');
    });

    it('shows the instrument panel for the instrument in the ticket', async () => {
      const fixture = await open({ symbol: 'AAPL' });

      expect(fixture.nativeElement.querySelector('[data-testid="instrument-symbol"]').textContent).toContain('AAPL');
      expect(fixture.nativeElement.querySelector('[data-testid="instrument-chart"]')).not.toBeNull();
    });

    it('shows the search prompt in place of details when no instrument is chosen', async () => {
      const fixture = await open({});

      expect(fixture.nativeElement.querySelector('[data-testid="instrument-panel-empty"]').textContent).toContain('Search for an instrument');
    });

    it('fills the ticket from the in-page search, drops the holding hint and syncs the URL', async () => {
      const fixture = await open({ symbol: 'MSFT', side: 'SELL', from: 'holding' });
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      const input: HTMLInputElement = fixture.nativeElement.querySelector('.ticket-search [data-testid="instrument-search-input"]');
      input.dispatchEvent(new Event('focus'));
      input.value = 'infosys';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
      fixture.detectChanges();

      const component = fixture.componentInstance;
      expect(component.symbol()).toBe('INFY.NS');
      expect(component.prefilledFrom()).toBeNull();
      expect(component.side()).toBe(OrderSide.Sell);
      expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({
        queryParams: { symbol: 'INFY.NS', from: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      }));
    });
  });
});
