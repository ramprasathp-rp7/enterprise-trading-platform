import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { InstrumentCatalogService } from './instrument-catalog.service';
import { CHART_RANGES } from '../instrument.models';
import { INFY_NS_DAILY } from '../data/infy-ns.candles';

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
        // 003_instruments.sql, in insert order so the catalogue ids match instruments.id
        'ACME', 'AAPL', 'INFY.NS', 'RELIANCE.NS', 'TATASTEEL.BO', 'FX:EURUSD', 'X:BTC-USD',
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
      expect(service.search('acme')[0].symbol).toBe('ACME');
      expect(service.search('a').map((i) => i.symbol).slice(0, 2)).toEqual(['AAPL', 'ACME']);
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

  describe('INFY.NS snapshot', () => {
    it('serves the captured Fauxnance candles as-is', () => {
      const series = service.candles('infy.ns')!;

      expect(service.dataSource('INFY.NS')).toBe('snapshot');
      expect(series).toBe(INFY_NS_DAILY);
      expect(series.currency).toBe('INR');
      expect(series.candles[0]).toEqual({
        date: '2025-10-06', open: 1446.6, high: 1478.8, low: 1442.3, close: 1476, adjclose: 1422.953, volume: 4630590, synthetic: false,
      });
      expect(series.candles.filter((c) => c.synthetic).every((c) => c.volume === null)).toBe(true);
    });

    it('quotes the last session close against the previous close, as a delayed, closed-market quote', () => {
      const q = service.quote('INFY.NS')!;

      expect(q.price).toBe(1036.7);
      expect(q.previousClose).toBe(1041.2);
      expect(q.change).toBe(-4.5);
      expect(q.changePercent).toBe(-0.43);
      expect(q.bid).toBeLessThan(q.price);
      expect(q.ask).toBeGreaterThan(q.price);
      expect(q.currency).toBe('INR');
      expect(q.marketState).toBe('closed');
      expect(q.stale).toBe(true);
      // 29 Jun 2026, 15:30 IST
      expect(q.quoteAsOf).toBe('2026-06-29T10:00:00.000Z');
    });

    it('describes the session before the quote (no open: candles never hold the quote day)', () => {
      const s = service.stats('INFY.NS', '3M')!;
      const candles = INFY_NS_DAILY.candles;

      // The quote is the 29 Jun close, so the previous session is 26 Jun (a flat, zero-volume day)
      expect(s.sessionDate).toBe('2026-06-26');
      expect(s.previousClose).toBe(1041.2);
      expect(s.previousHigh).toBe(1041.2);
      expect(s.previousLow).toBe(1041.2);
      expect(s.previousVolume).toBe(0);
      expect(s).not.toHaveProperty('open');
      expect(s.price).toBe(1036.7);
      expect(s.yearHigh).toBe(Math.max(...candles.map((c) => c.high)));
      expect(s.yearLow).toBe(Math.min(...candles.map((c) => c.low)));
      expect(s.averageVolume30d).toBeGreaterThan(0);
      expect(s.rangeReturnPercent).toBeLessThan(0);
    });

    it('slices ranges back from the latest candle', () => {
      const week = service.candlesInRange('INFY.NS', '1W');
      expect(week.map((c) => c.date)).toEqual(['2026-06-23', '2026-06-24', '2026-06-25', '2026-06-26', '2026-06-29']);

      const lengths = CHART_RANGES.map((r) => service.candlesInRange('INFY.NS', r).length);
      expect(lengths).toEqual([...lengths].sort((a, b) => a - b));
      // The longest range is 3 months (candle data is kept for 90 days), so it is a slice of the snapshot
      const quarter = service.candlesInRange('INFY.NS', '3M');
      expect(quarter.length).toBeLessThan(INFY_NS_DAILY.candles.length);
      expect(quarter[quarter.length - 1]).toEqual(INFY_NS_DAILY.candles[INFY_NS_DAILY.candles.length - 1]);
    });
  });

  describe('sample instruments', () => {
    it('generates weekday candles for completed sessions only, never today', () => {
      const series = service.candles('AAPL')!;
      const last = series.candles[series.candles.length - 1];

      expect(service.dataSource('AAPL')).toBe('sample');
      expect(series.candles.length).toBe(260);
      // NOW is Monday 5 Oct, so the latest candle is Friday 2 Oct
      expect(last.date).toBe('2026-10-02');
      expect(series.candles.some((c) => c.date === '2026-10-05')).toBe(false);
      expect(last.synthetic).toBe(false);
      expect(series.candles.every((c) => c.low <= Math.min(c.open, c.close) && c.high >= Math.max(c.open, c.close))).toBe(true);
      expect(series.candles.every((c) => ![0, 6].includes(new Date(`${c.date}T00:00:00Z`).getUTCDay()))).toBe(true);
    });

    it('is deterministic and cached per symbol', () => {
      expect(service.candles('ACME')).toBe(service.candles('ACME'));
      expect(service.candles('INFY.NS')!.candles).not.toEqual(service.candles('ACME')!.candles);
    });

    it('trades crypto every day and gives FX no volume', () => {
      const btc = service.candles('X:BTC-USD')!.candles;
      expect(btc.some((c) => new Date(`${c.date}T00:00:00Z`).getUTCDay() === 0)).toBe(true);

      const fx = service.candles('FX:EURUSD')!.candles;
      expect(fx.every((c) => c.volume === null)).toBe(true);
      expect(service.decimals('FX:EURUSD')).toBe(4);
      expect(service.decimals('AAPL')).toBe(2);
    });

    it('quotes a live price for today against the last candle as previous close', () => {
      const q = service.quote('AAPL')!;
      const candles = service.candles('AAPL')!.candles;
      const lastClose = candles[candles.length - 1].close;

      expect(q.price).toBe(189.35);
      expect(q.previousClose).toBe(lastClose);
      expect(q.change).toBeCloseTo(189.35 - lastClose, 2);
      expect(q.stale).toBe(false);
      expect(q.quoteAsOf).toBe(NOW.toISOString());
      expect(q.marketState).toBe('open');
    });

    it('takes "previous session" stats from the last candle and price stats from the quote', () => {
      const s = service.stats('AAPL', '1M')!;
      const candles = service.candles('AAPL')!.candles;
      const last = candles[candles.length - 1];
      const first = service.candlesInRange('AAPL', '1M')[0];

      expect(s.sessionDate).toBe(last.date);
      expect(s.previousClose).toBe(last.close);
      expect(s.previousHigh).toBe(last.high);
      expect(s.previousLow).toBe(last.low);
      expect(s.previousVolume).toBe(last.volume);
      expect(s.rangeReturnPercent).toBeCloseTo(((189.35 - first.close) / first.close) * 100, 6);
      expect(s.yearLow).toBeLessThanOrEqual(189.35);
      expect(s.yearHigh).toBeGreaterThanOrEqual(189.35);
    });

    it('has no data for unknown symbols', () => {
      expect(service.candles('NOPE')).toBeNull();
      expect(service.candlesInRange('NOPE', '1M')).toEqual([]);
      expect(service.stats('NOPE', '1M')).toBeNull();
      expect(service.quote('NOPE')).toBeNull();
      expect(service.dataSource('NOPE')).toBe('sample');
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
