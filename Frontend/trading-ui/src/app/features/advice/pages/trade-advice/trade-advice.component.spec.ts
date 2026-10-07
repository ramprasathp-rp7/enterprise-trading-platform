import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { TradeAdviceComponent } from './trade-advice.component';
import { SignalResponse, TradeAdviceResponse, TradeAdviceService } from '../../../../api/generated/advice';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';

describe('TradeAdviceComponent', () => {
  let fixture: ComponentFixture<TradeAdviceComponent>;
  let component: TradeAdviceComponent;
  let api: { getTradeAdvice: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const signal = (symbol: string, value: SignalResponse['signal'], extra: Partial<SignalResponse> = {}): SignalResponse => ({
    symbol,
    name: `${symbol} Inc.`,
    currency: symbol.endsWith('.NS') ? 'INR' : 'USD',
    held: false,
    watched: true,
    signal: value,
    confidence: value ? 72 : null,
    summary: value ? `Signals favour ${value === 'BUY' ? 'buying: an uptrend' : 'selling or reducing: a downtrend'}.` : 'Only 12 days of price history; at least 50 are needed for a reliable signal.',
    reasons: value
      ? [
          { factor: 'Trend', impact: value === 'BUY' ? 'POSITIVE' : 'NEGATIVE', detail: 'Uptrend: the 20-day average (1,010.00) is above the 50-day average (980.00).' },
          { factor: 'RSI(14)', impact: 'NEUTRAL', detail: 'RSI is 52, between oversold (30) and overbought (70).' },
        ]
      : [{ factor: 'Data', impact: 'NEUTRAL', detail: 'Only 12 days of price history.' }],
    indicators: { price: 1006.35, sma20: 1010, sma50: 980, rsi14: 52.3, momentum20Percent: 3.2, unrealisedPercent: null, daysOfHistory: value ? 120 : 12 },
    ...extra,
  });

  const advice = (signals: SignalResponse[]): TradeAdviceResponse => ({
    accountId: 7,
    generatedAt: '2026-10-07T09:00:00Z',
    disclaimer: 'Signals come from simple technical rules on recent prices. They are educational, not investment advice.',
    signals,
  });

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TradeAdviceComponent],
      providers: [
        provideRouter([]),
        { provide: TradeAdviceService, useValue: api },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TradeAdviceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const q = (sel: string): HTMLElement => fixture.nativeElement.querySelector(sel);
  const qa = (sel: string): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll(sel));

  beforeEach(() => {
    currentUser = vi.fn(() => ({ accountId: 7 }));
    api = {
      getTradeAdvice: vi.fn(() =>
        of(advice([
          signal('INFY.NS', 'SELL', { held: true, watched: false, indicators: { price: 1006.35, sma20: 1, sma50: 2, rsi14: 41, momentum20Percent: -6.1, unrealisedPercent: -33.8, daysOfHistory: 120 } }),
          signal('AAPL', 'BUY'),
          signal('ACME', null),
        ])),
      ),
    };
  });

  it('shows each signal with its reasoning, and the instruments without enough data separately', async () => {
    await create();

    expect(api.getTradeAdvice).toHaveBeenCalledWith(7);
    const infy = q('[data-testid="advice-INFY.NS"]');
    expect(infy.querySelector('[data-testid="signal-badge"]')!.textContent).toContain('SELL');
    expect(infy.querySelector('[data-testid="signal-summary"]')!.textContent).toContain('Signals favour selling');
    expect(infy.querySelectorAll('.reason').length).toBe(2);
    expect(infy.textContent).toContain('Uptrend: the 20-day average');
    expect(infy.textContent).toContain('₹1,006.35');
    expect(infy.textContent).toContain('−33.8%');
    expect(infy.textContent).toContain('Held');
    // a held instrument offers Sell; a watched one only Buy
    expect(infy.querySelectorAll('.trade-btn').length).toBe(2);
    expect(q('[data-testid="advice-AAPL"]').querySelectorAll('.trade-btn').length).toBe(1);

    expect(q('[data-testid="advice-ACME"]')).toBeNull();
    expect(q('[data-testid="no-signal-ACME"]').textContent).toContain('Only 12 days of price history');

    expect(q('[data-testid="count-buy"]').textContent).toContain('1');
    expect(q('[data-testid="count-sell"]').textContent).toContain('1');
    expect(q('.disclaimer').textContent).toContain('not investment advice');
  });

  it('filters to holdings or watchlist', async () => {
    await create();

    q('[data-testid="filter-held"]').click();
    fixture.detectChanges();
    expect(qa('.signal-card').map((el) => el.getAttribute('data-testid'))).toEqual(['advice-INFY.NS']);
    expect(q('[data-testid="advice-insufficient"]')).toBeNull();

    q('[data-testid="filter-watched"]').click();
    fixture.detectChanges();
    expect(qa('.signal-card').map((el) => el.getAttribute('data-testid'))).toEqual(['advice-AAPL']);
  });

  it('explains what to do when nothing is held or watched', async () => {
    api.getTradeAdvice.mockReturnValue(of(advice([])));
    await create();

    expect(q('[data-testid="advice-empty"]').textContent).toContain('Nothing to advise on yet');
  });

  it('says so when the account is not the signed-in one (403), and retries on Refresh', async () => {
    api.getTradeAdvice.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await create();

    expect(q('[data-testid="advice-error"]').textContent).toContain('only see advice for your own account');

    api.getTradeAdvice.mockReturnValue(of(advice([signal('AAPL', 'BUY')])));
    q('[data-testid="refresh-advice"]').click();
    fixture.detectChanges();
    expect(q('[data-testid="advice-error"]')).toBeNull();
    expect(q('[data-testid="advice-AAPL"]')).not.toBeNull();
  });

  it('does not call the API without a signed-in account', async () => {
    currentUser.mockReturnValue(null);
    await create();

    expect(api.getTradeAdvice).not.toHaveBeenCalled();
    expect(q('[data-testid="advice-error"]').textContent).toContain('No account found');
  });
});
