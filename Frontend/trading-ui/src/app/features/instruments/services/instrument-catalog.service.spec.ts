import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { InstrumentCatalogService } from './instrument-catalog.service';
import { CHART_RANGES, Candle } from '../instrument.models';
import { CachedQuoteResponse } from '../../../api/generated/market-data';

describe('InstrumentCatalogService', () => {
  let service: InstrumentCatalogService;
  /** Monday 5 Oct 2026, 15:00 UTC = 11:00 New York, 20:30 Mumbai */
  const NOW = new Date('2026-10-05T15:00:00Z');

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(InstrumentCatalogService);
    service.now = () => NOW;
  });

  describe('catalogue', () => {
    it('mirrors the seeded INSTRUMENTS table, including the non-tradable one', () => {
      expect(service.all().map((i) => i.symbol)).toEqual([
        // 003_instruments.sql
        'PFE', 'AAPL', 'INFY.NS', 'RELIANCE.NS', 'TATASTEEL.BO', 'FX:EURUSD', 'X:BTC-USD',
        // 007_additional_instruments.sql
        'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'TSLA', 'META', 'JPM', 'NFLX',
        'TCS.NS', 'HDFCBANK.NS', 'ICICIBANK.NS', 'WIPRO.NS',
        'FX:GBPUSD', 'FX:USDJPY', 'FX:USDINR', 'X:ETH-USD', 'X:SOL-USD', 'X:XRP-USD',
      ]);
      expect(service.find('TATASTEEL.BO')?.tradable).toBe(false);
      expect(service.all().map((i) => i.id)).toEqual(service.all().map((_, index) => index + 1));
    });

    it('finds by symbol case-insensitively', () => {
      expect(service.find(' infy.ns ')?.name).toBe('Infosys Limited');
      expect(service.find('NOPE')).toBeNull();
      expect(service.find(null)).toBeNull();
    });
  });

  describe('search', () => {
    it('returns the catalogue for an empty query, capped at the limit', () => {
      expect(service.all().length).toBe(25);
      expect(service.search('').length).toBe(8);
      expect(service.search('   ', 3).length).toBe(3);
    });

    it('ranks exact symbol, then symbol prefix, then name word, then substring', () => {
      expect(service.search('pfe')[0].symbol).toBe('PFE');
      expect(service.search('pfizer')[0].symbol).toBe('PFE');
      expect(service.search('a').map((i) => i.symbol).slice(0, 2)).toEqual(['AAPL', 'AMZN']);
      expect(service.search('infosys').map((i) => i.symbol)).toEqual(['INFY.NS']);
      expect(service.search('steel').map((i) => i.symbol)).toEqual(['TATASTEEL.BO']);
    });

    it('matches FX and crypto symbols without their prefix, and by name', () => {
      expect(service.search('eurusd')[0].symbol).toBe('FX:EURUSD');
      expect(service.search('btc')[0].symbol).toBe('X:BTC-USD');
      expect(service.search('bitcoin')[0].symbol).toBe('X:BTC-USD');
      expect(service.search('dollar', 25).map((i) => i.symbol)).toEqual(
        expect.arrayContaining(['FX:EURUSD', 'FX:USDJPY', 'X:BTC-USD', 'X:ETH-USD']),
      );
      expect(service.search('yen')[0].symbol).toBe('FX:USDJPY');
    });

    it('returns nothing for an unknown query', () => {
      expect(service.search('zzz')).toEqual([]);
    });
  });

  describe('market data from the APIs', () => {
    /** Daily candles from the candle API, Mon 3 Aug to Fri 2 Oct 2026 (weekdays) */
    const history: Candle[] = (() => {
      const out: Candle[] = [];
      for (let d = new Date('2026-08-03T00:00:00Z'); d <= new Date('2026-10-02T00:00:00Z'); d = new Date(d.getTime() + 86_400_000)) {
        if (d.getUTCDay() === 0 || d.getUTCDay() === 6) continue;
        const close = 100 + out.length;
        out.push({
          date: d.toISOString().slice(0, 10),
          open: close - 0.5, high: close + 1, low: close - 1, close, adjclose: close,
          volume: 1000 + out.length, synthetic: false,
        });
      }
      return out;
    })();
    const last = history[history.length - 1];
    const aapl = () => service.find('AAPL')!;
    const cached = (over: Partial<CachedQuoteResponse> = {}): CachedQuoteResponse => ({
      symbol: 'AAPL', price: 150, bid: 149.98, ask: 150.02, currency: 'USD',
      change: null, changePercent: null, previousClose: null, marketState: null, stale: false,
      quoteAsOf: '2026-10-05T15:00:00Z', receivedAt: '2026-10-05T15:00:05Z', ...over,
    });

    it('slices candle ranges back from the latest candle, and none for Today', () => {
      expect(service.candlesInRange(history, '1W').map((c) => c.date)).toEqual([
        '2026-09-28', '2026-09-29', '2026-09-30', '2026-10-01', '2026-10-02',
      ]);
      expect(service.candlesInRange(history, '1M')[0].date).toBe('2026-09-03');
      expect(service.candlesInRange(history, '3M').length).toBe(history.length);
      expect(service.candlesInRange(history, 'Today')).toEqual([]);
      expect(service.candlesInRange([], '3M')).toEqual([]);
      expect(CHART_RANGES).toEqual(['Today', '1W', '1M', '3M']);
    });

    it('builds the quote from the cache, deriving change and state the Fauxnance feed leaves out', () => {
      const quote = service.toQuote(aapl(), cached(), history);

      expect(quote).toEqual({
        symbol: 'AAPL', price: 150, bid: 149.98, ask: 150.02, currency: 'USD',
        previousClose: last.close,
        change: 150 - last.close,
        changePercent: Math.round(((150 - last.close) / last.close) * 10000) / 100,
        marketState: 'open', // 11:00 ET on a Monday
        stale: false,
        delayMinutes: null,
        quoteAsOf: '2026-10-05T15:00:00Z',
      });
    });

    it("works out the market state from the exchange clock when the feed says 'unknown'", () => {
      expect(service.toQuote(aapl(), cached({ marketState: 'unknown' }), history).marketState).toBe('open'); // 11:00 ET
      expect(service.toQuote(service.find('INFY.NS')!, cached({ symbol: 'INFY.NS', marketState: 'unknown' }), history).marketState)
        .toBe('closed'); // 20:30 IST
    });

    it("ignores the feed's stale flag: delayed only while the market is open and the quote is over 15 minutes old", () => {
      // Fresh quote flagged stale by Fauxnance's own cache
      expect(service.toQuote(aapl(), cached({ stale: true }), history).stale).toBe(false);

      // Market open, quote from 10:35 ET (25 minutes ago)
      const late = service.toQuote(aapl(), cached({ quoteAsOf: '2026-10-05T14:35:00Z' }), history);
      expect(late.stale).toBe(true);
      expect(late.delayMinutes).toBe(25);

      // 15 minutes exactly is still fresh
      expect(service.toQuote(aapl(), cached({ quoteAsOf: '2026-10-05T14:45:00Z' }), history).stale).toBe(false);

      // NSE is closed at 20:30 IST: yesterday's price is expected, not delayed
      const infy = service.toQuote(service.find('INFY.NS')!, cached({ symbol: 'INFY.NS', quoteAsOf: '2026-10-05T10:00:00Z', stale: true }), history);
      expect(infy.stale).toBe(false);
      expect(infy.delayMinutes).toBeNull();
    });

    it('prefers the figures the quote carries (market-data topic quotes)', () => {
      const quote = service.toQuote(aapl(), cached({ previousClose: 148, change: 2, changePercent: 1.35, marketState: 'CLOSED', stale: true }), history);

      expect(quote.previousClose).toBe(148);
      expect(quote.change).toBe(2);
      expect(quote.changePercent).toBe(1.35);
      expect(quote.marketState).toBe('closed');
      // Closed market: an old quote is expected, so not shown as delayed
      expect(quote.stale).toBe(false);
    });

    it('uses receivedAt when quoteAsOf is missing, and has no change without candles', () => {
      const quote = service.toQuote(aapl(), cached({ quoteAsOf: null }), []);

      expect(quote.quoteAsOf).toBe('2026-10-05T15:00:05Z');
      expect(quote.previousClose).toBeNull();
      expect(quote.change).toBeNull();
      expect(quote.changePercent).toBeNull();
    });

    it('computes previous-session, volume and high/low stats from candles and the quote', () => {
      const quote = service.toQuote(aapl(), cached(), history);
      const stats = service.stats(aapl(), history, quote, '1W')!;
      const recent = history.slice(-30);

      expect(stats.sessionDate).toBe('2026-10-02');
      expect(stats.previousClose).toBe(last.close);
      expect(stats.previousHigh).toBe(last.high);
      expect(stats.previousLow).toBe(last.low);
      expect(stats.previousVolume).toBe(last.volume);
      expect(stats.averageVolume30d).toBe(Math.round(recent.reduce((s, c) => s + c.volume!, 0) / 30));
      expect(stats.price).toBe(150);
      expect(stats.yearHigh).toBe(150); // the quote is above every candle high
      expect(stats.yearLow).toBe(history[0].low);
      expect(stats.historyFrom).toBe('2026-08-03');
      const firstOfWeek = service.candlesInRange(history, '1W')[0].close;
      expect(stats.rangeReturnPercent).toBeCloseTo(((150 - firstOfWeek) / firstOfWeek) * 100, 10);
    });

    it('skips synthetic bars for volume and falls back to the last close without a quote', () => {
      const withGap = [...history.slice(0, -1), { ...last, synthetic: true, volume: null }];
      const stats = service.stats(aapl(), withGap, null, 'Today')!;

      expect(stats.previousVolume).toBeNull();
      expect(stats.price).toBe(last.close);
      expect(stats.previousClose).toBe(last.close);
      expect(stats.rangeReturnPercent).toBeNull();
      expect(service.stats(aapl(), [], null, '3M')).toBeNull();
    });

    it('treats a candle on the quote day as today, not the previous session', () => {
      const today = { ...last, date: '2026-10-05' };
      const stats = service.stats(aapl(), [...history, today], service.toQuote(aapl(), cached(), history), '3M')!;

      expect(stats.sessionDate).toBe('2026-10-02');
    });

    it('dates trading days in the exchange time zone', () => {
      // 20:00 UTC Monday is already Tuesday in India; still Monday in New York and UTC
      expect(service.tradingDay(service.find('INFY.NS')!, '2026-10-05T20:00:00Z')).toBe('2026-10-06');
      expect(service.tradingDay(aapl(), '2026-10-05T20:00:00Z')).toBe('2026-10-05');
      expect(service.tradingDay(service.find('X:BTC-USD')!, '2026-10-05T20:00:00Z')).toBe('2026-10-05');
    });

    it('formats FX to 4 decimals and everything else to 2', () => {
      expect(service.decimals('FX:EURUSD')).toBe(4);
      expect(service.decimals('AAPL')).toBe(2);
    });
  });

  describe('market state', () => {
    const at = (iso: string) => new Date(iso);
    const state = (symbol: string, iso: string) => service.marketState(service.find(symbol)!, at(iso));

    it('follows US equity sessions in New York time', () => {
      expect(state('AAPL', '2026-10-05T12:00:00Z')).toBe('pre'); // 08:00 ET
      expect(state('AAPL', '2026-10-05T15:00:00Z')).toBe('open'); // 11:00 ET
      expect(state('AAPL', '2026-10-05T21:00:00Z')).toBe('post'); // 17:00 ET
      expect(state('AAPL', '2026-10-06T02:00:00Z')).toBe('closed'); // 22:00 ET
      expect(state('AAPL', '2026-10-03T15:00:00Z')).toBe('closed'); // Saturday
    });

    it('follows NSE sessions in India time', () => {
      expect(state('RELIANCE.NS', '2026-10-05T03:35:00Z')).toBe('pre'); // 09:05 IST
      expect(state('RELIANCE.NS', '2026-10-05T05:00:00Z')).toBe('open'); // 10:30 IST
      expect(state('RELIANCE.NS', '2026-10-05T10:15:00Z')).toBe('post'); // 15:45 IST
      expect(state('RELIANCE.NS', '2026-10-05T15:00:00Z')).toBe('closed'); // 20:30 IST
    });

    it('treats FX as a weekday market and crypto as always open', () => {
      expect(state('FX:EURUSD', '2026-10-05T15:00:00Z')).toBe('open');
      expect(state('FX:EURUSD', '2026-10-03T12:00:00Z')).toBe('closed');
      expect(state('FX:EURUSD', '2026-10-02T23:00:00Z')).toBe('closed'); // Friday after 22:00 UTC
      expect(state('FX:EURUSD', '2026-10-04T23:00:00Z')).toBe('open'); // Sunday after 22:00 UTC
      expect(state('X:BTC-USD', '2026-10-03T12:00:00Z')).toBe('open');
    });

    it('knows each exchange clock', () => {
      expect(service.exchangeHours(service.find('INFY.NS')!)?.label).toBe('IST');
      expect(service.exchangeHours(service.find('AAPL')!)?.label).toBe('ET');
      expect(service.exchangeHours(service.find('FX:EURUSD')!)).toBeNull();
    });
  });
});
