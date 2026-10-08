import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { InstrumentPanelComponent } from './instrument-panel.component';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';
import { CandleService } from '../../services/candle.service';
import { QuoteIntraDayService } from '../../services/quote-intraday.service';
import { CachedQuoteResponse, MarketDataService } from '../../../../api/generated/market-data';
import { Candle, QuotePoint } from '../../instrument.models';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';

/** Weekday candles Mon 3 Aug – Fri 2 Oct 2026, closes 100, 101, … */
function candles(): Candle[] {
  const out: Candle[] = [];
  for (let d = new Date('2026-08-03T00:00:00Z'); d <= new Date('2026-10-02T00:00:00Z'); d = new Date(d.getTime() + 86_400_000)) {
    if (d.getUTCDay() === 0 || d.getUTCDay() === 6) continue;
    const close = 100 + out.length;
    out.push({ date: d.toISOString().slice(0, 10), open: close - 0.5, high: close + 1, low: close - 1, close, adjclose: close, volume: 5000, synthetic: false });
  }
  return out;
}

const AAPL_QUOTE: CachedQuoteResponse = {
  symbol: 'AAPL', price: 150, bid: 149.95, ask: 150.05, currency: 'USD',
  change: null, changePercent: null, previousClose: null, marketState: null, stale: false,
  quoteAsOf: '2026-10-05T15:00:00Z', receivedAt: '2026-10-05T15:00:02Z',
};

const INTRADAY: QuotePoint[] = [
  { time: '2026-10-05T13:30:00Z', price: 141, bid: 140.95, ask: 141.05 },
  { time: '2026-10-05T14:30:00Z', price: 148, bid: 147.95, ask: 148.05 },
  { time: '2026-10-05T15:00:00Z', price: 150, bid: 149.95, ask: 150.05 },
];

const notCached = () => throwError(() => new HttpErrorResponse({ status: 404 }));

describe('InstrumentPanelComponent', () => {
  let fixture: ComponentFixture<InstrumentPanelComponent>;
  let component: InstrumentPanelComponent;
  let getHistory: ReturnType<typeof vi.fn>;
  let getLatestQuote: ReturnType<typeof vi.fn>;
  let getQuotesForToday: ReturnType<typeof vi.fn>;

  const show = (symbol: string | null) => {
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    fixture.detectChanges();
  };
  const el = (sel: string): HTMLElement | null => fixture.nativeElement.querySelector(sel);
  const text = (sel: string) => el(sel)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const click = (sel: string) => {
    el(sel)!.click();
    fixture.detectChanges();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    getHistory = vi.fn((): Observable<Candle[]> => of(candles()));
    getLatestQuote = vi.fn((symbol: string) => (symbol === 'AAPL' ? of(AAPL_QUOTE) : notCached()));
    getQuotesForToday = vi.fn(() => of(INTRADAY));

    await TestBed.configureTestingModule({
      imports: [InstrumentPanelComponent],
      providers: [
        { provide: CandleService, useValue: { getHistory } },
        { provide: MarketDataService, useValue: { getLatestQuote } },
        { provide: QuoteIntraDayService, useValue: { getQuotesForToday } },
      ],
    }).compileComponents();
    // Monday 5 Oct 2026, 11:00 in New York
    const catalog = TestBed.inject(InstrumentCatalogService);
    catalog.now = () => new Date('2026-10-05T15:00:00Z');
    fixture = TestBed.createComponent(InstrumentPanelComponent);
    component = fixture.componentInstance;
  });

  it('prompts to search when no instrument is chosen, without fetching', () => {
    show(null);

    expect(text('[data-testid="instrument-panel-empty"]')).toContain('Search for an instrument');
    expect(getHistory).not.toHaveBeenCalled();
    expect(getLatestQuote).not.toHaveBeenCalled();
  });

  it('says when the symbol is not in the catalogue', () => {
    show('nope');

    expect(text('[data-testid="instrument-panel-empty"]')).toContain('No details for “NOPE”');
  });

  describe('AAPL with a cached quote and candles', () => {
    beforeEach(() => show('aapl'));

    it('fetches candles by instrument id and the quote by symbol', () => {
      expect(getHistory).toHaveBeenCalledWith(2);
      expect(getLatestQuote).toHaveBeenCalledWith('AAPL');
      expect(text('[data-testid="data-source"]')).toBe('Market data');
      expect(text('[data-testid="data-source"]')).not.toContain('Sample');
    });

    it('shows the cached price with change against the last candle close', () => {
      // last candle close is 144 (45 weekdays from 3 Aug)
      expect(text('[data-testid="instrument-symbol"]')).toBe('AAPL');
      expect(text('[data-testid="instrument-last"]')).toBe('150.00');
      expect(text('[data-testid="instrument-change"]')).toBe('+$6.00 (+4.17%)');
      expect(text('[data-testid="market-state"]')).toBe('Market open');
      expect(el('[data-testid="quote-stale"]')).toBeNull();
      expect(text('[data-testid="quote-as-of"]')).toBe('as of Oct 5, 11:00 AM ET');
    });

    it('shows the cached bid, ask and spread', () => {
      const book = text('[data-testid="quote-book"]');
      expect(book).toContain('Bid $149.95');
      expect(book).toContain('Ask $150.05');
      expect(book).toContain('Spread $0.10');
    });

    it('shows previous-session stats from the candles and labels the high/low by the period covered', () => {
      const stats = text('[data-testid="instrument-stats"]');
      expect(stats).toContain('Prev close$144.00');
      expect(stats).toContain('Prev day range$143.00 – $145.00');
      expect(stats).toContain('Prev volume5,000');
      expect(stats).toContain('Avg vol (30D)5,000');
      expect(component.sessionTitle()).toBe('Previous session: Fri, Oct 2, 2026');
      expect(text('[data-testid="year-range-label"]')).toBe('Range since Aug 3');
      expect(stats).toContain('99.00');
      expect(stats).toContain('150.00');
      expect(component.yearPosition()).toBe(100);
    });

    it('defaults to 3 months of candles; ranges slice the same history without refetching', () => {
      expect(component.range()).toBe('3M');
      expect(component.series().length).toBe(candles().length);
      expect(text('[data-testid="range-return"]')).toBe('3M +50.00%');
      expect(el('[data-testid="instrument-chart"]')).not.toBeNull();

      click('[data-testid="range-1W"]');
      expect(component.series().length).toBe(5);
      expect(getHistory).toHaveBeenCalledTimes(1);

      click('[data-testid="mode-line"]');
      expect(el('[data-testid="chart-line"]')).not.toBeNull();
    });

    it('plots the day\'s cached quotes on the Today range', () => {
      click('[data-testid="range-Today"]');

      expect(getQuotesForToday).toHaveBeenCalledWith('AAPL');
      expect(el('[data-testid="instrument-chart"]')).toBeNull();
      expect(el('[data-testid="quotes-chart"]')).not.toBeNull();
      expect(text('[data-testid="quotes-last-tag"]')).toBe('150.00');
      expect(text('[data-testid="range-return"]')).toBe('Today +4.17%');
    });
  });

  it('says when no quote is cached yet, and still shows candles and stats', () => {
    show('MSFT');

    expect(el('[data-testid="instrument-last"]')).toBeNull();
    expect(el('[data-testid="quote-status"]')?.getAttribute('data-status')).toBe('missing');
    expect(text('[data-testid="quote-status"]')).toContain('No quote cached for MSFT yet');
    expect(el('[data-testid="instrument-chart"]')).not.toBeNull();
    expect(text('[data-testid="instrument-stats"]')).toContain('Prev close$144.00');
  });

  it('shows an error with Retry when candles fail, and recovers', () => {
    getHistory.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    show('AAPL');

    expect(text('[data-testid="chart-error"]')).toContain("Couldn't load price history");
    expect(el('[data-testid="instrument-stats"]')).toBeNull();

    click('[data-testid="chart-error"] .retry');
    expect(getHistory).toHaveBeenCalledTimes(2);
    expect(el('[data-testid="instrument-chart"]')).not.toBeNull();
  });

  describe('staying on Today', () => {
    const NEXT: QuotePoint = { time: '2026-10-05T15:05:00Z', price: 152, bid: 151.95, ask: 152.05 };

    afterEach(() => vi.useRealTimers());

    it('redraws as soon as the headline quote changes, without switching range', () => {
      show('AAPL');
      click('[data-testid="range-Today"]');
      expect(getQuotesForToday).toHaveBeenCalledTimes(1);

      // The cache refreshed: the headline store picks up a newer quote
      getQuotesForToday.mockReturnValue(of([...INTRADAY, NEXT]));
      getLatestQuote.mockReturnValue(of({ ...AAPL_QUOTE, price: 152, quoteAsOf: NEXT.time, receivedAt: '2026-10-05T15:05:02Z' }));
      TestBed.inject(LiveQuoteStore).refresh(['AAPL'], true);
      fixture.detectChanges();
      fixture.detectChanges();

      expect(getQuotesForToday).toHaveBeenCalledTimes(2);
      expect(text('[data-testid="quotes-last-tag"]')).toBe('152.00');
      expect(text('[data-testid="instrument-last"]')).toBe('152.00');
    });

    it('rereads the cache every minute and pulls the headline forward when the chart is newer', () => {
      vi.useFakeTimers();
      show('AAPL');
      click('[data-testid="range-Today"]');
      expect(getQuotesForToday).toHaveBeenCalledTimes(1);
      const quoteCalls = getLatestQuote.mock.calls.length;

      getQuotesForToday.mockReturnValue(of([...INTRADAY, NEXT]));
      vi.advanceTimersByTime(60_000);
      fixture.detectChanges();

      expect(getQuotesForToday).toHaveBeenCalledTimes(2);
      expect(text('[data-testid="quotes-last-tag"]')).toBe('152.00');
      // NEXT is newer than the headline's receivedAt, so the headline is refetched at once
      expect(getLatestQuote.mock.calls.length).toBe(quoteCalls + 1);
    });

    it('keeps the same data object when nothing changed, so the chart is not redrawn', () => {
      vi.useFakeTimers();
      show('AAPL');
      click('[data-testid="range-Today"]');
      const before = component.intraday();

      vi.advanceTimersByTime(60_000);

      expect(getQuotesForToday).toHaveBeenCalledTimes(2);
      expect(component.intraday()).toBe(before);
    });

    it('skips ticks while the tab is hidden', () => {
      vi.useFakeTimers();
      show('AAPL');
      click('[data-testid="range-Today"]');
      const hidden = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');

      vi.advanceTimersByTime(180_000);
      expect(getQuotesForToday).toHaveBeenCalledTimes(1);

      hidden.mockReturnValue('visible');
      document.dispatchEvent(new Event('visibilitychange'));
      expect(getQuotesForToday).toHaveBeenCalledTimes(2);
    });
  });

  it('shows an empty state when no quotes are cached today', () => {
    getQuotesForToday.mockReturnValue(of([]));
    show('AAPL');
    click('[data-testid="range-Today"]');

    expect(text('[data-testid="quotes-chart-empty"]')).toContain('No quotes cached today yet');
  });

  it('warns when the instrument is not tradable', () => {
    show('TATASTEEL.BO');

    const note = el('[data-testid="not-tradable-note"]')!;
    expect(note.textContent!.trim()).toBe('Not tradable');
    expect(note.getAttribute('title')).toBe('TATASTEEL.BO is currently not tradable. Orders for it will be rejected.');
  });

  it('formats FX prices to 4 decimals with asset-specific icons', () => {
    show('FX:EURUSD');
    expect(component.decimals()).toBe(4);
    expect(component.assetIcon()).toBe('fa-money-bill-transfer');

    show('X:BTC-USD');
    expect(component.assetIcon()).toBe('fa-coins');

    show('ACME');
    expect(component.assetIcon()).toBe('fa-building');
  });

  it('shows how far behind a quote is while the market is open, and never "Status unknown" from the feed', () => {
    getLatestQuote.mockReturnValue(of({ ...AAPL_QUOTE, marketState: 'unknown', stale: true, quoteAsOf: '2026-10-05T13:55:00Z' }));
    show('AAPL');

    expect(text('[data-testid="market-state"]')).toBe('Market open');
    expect(text('[data-testid="quote-stale"]')).toBe('Delayed 1 h 5 min');
    expect(component.delayLabel(25)).toBe('25 min');
    expect(component.delayLabel(120)).toBe('2 h');
  });

  it('does not flag a fresh quote as delayed even if the feed does', () => {
    getLatestQuote.mockReturnValue(of({ ...AAPL_QUOTE, marketState: 'unknown', stale: true }));
    show('AAPL');

    expect(el('[data-testid="quote-stale"]')).toBeNull();
  });

  it('maps every market state to a label', () => {
    expect(['open', 'closed', 'pre', 'post', 'unknown'].map((s) => component.marketStateLabel(s as any))).toEqual([
      'Market open', 'Market closed', 'Pre-market', 'After hours', 'Status unknown',
    ]);
  });
});
