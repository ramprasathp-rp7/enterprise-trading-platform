import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { InstrumentCatalogService } from './instrument-catalog.service';
import { CHART_RANGES } from '../instrument.models';

describe('InstrumentCatalogService', () => {
  let service: InstrumentCatalogService;
  const NOW = new Date('2026-10-05T15:00:00Z');

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(InstrumentCatalogService);
    service.now = () => NOW;
  });

  describe('catalogue', () => {
    it('mirrors the seeded INSTRUMENTS table, including the non-tradable one', () => {
      expect(service.all().map((i) => i.symbol)).toEqual([
        'AAPL', 'ACME', 'INFY.NS', 'RELIANCE.NS', 'TATASTEEL.BO', 'FX:EURUSD', 'X:BTC-USD',
      ]);
      expect(service.find('TATASTEEL.BO')?.tradable).toBe(false);
    });

    it('finds by symbol case-insensitively', () => {
      expect(service.find(' infy.ns ')?.name).toBe('Infosys Limited');
      expect(service.find('NOPE')).toBeNull();
      expect(service.find(null)).toBeNull();
    });
  });

  describe('search', () => {
    it('returns the catalogue for an empty query', () => {
      expect(service.search('').length).toBe(7);
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
      expect(service.search('dollar').map((i) => i.symbol)).toEqual(['FX:EURUSD', 'X:BTC-USD']);
    });

    it('returns nothing for an unknown query', () => {
      expect(service.search('zzz')).toEqual([]);
    });
  });

  describe('sample market data', () => {
    it('produces a history for every range that ends at the same last price, at "now"', () => {
      const lasts = CHART_RANGES.map((r) => {
        const h = service.history('AAPL', r);
        expect(h.length).toBeGreaterThan(20);
        expect(h[h.length - 1].time.getTime()).toBe(NOW.getTime());
        expect(h[0].time.getTime()).toBeLessThan(NOW.getTime());
        return h[h.length - 1].price;
      });
      expect(new Set(lasts)).toEqual(new Set([189.35]));
    });

    it('is deterministic for the same symbol and range', () => {
      expect(service.history('INFY.NS', '1M')).toEqual(service.history('INFY.NS', '1M'));
      expect(service.history('INFY.NS', '1M')).not.toEqual(service.history('ACME', '1M'));
    });

    it('uses 4 decimals for FX and 2 otherwise', () => {
      expect(service.decimals('FX:EURUSD')).toBe(4);
      expect(service.decimals('AAPL')).toBe(2);
      const fx = service.history('FX:EURUSD', '1D');
      expect(fx.every((p) => Number.isInteger(Math.round(p.price * 1e4)))).toBe(true);
    });

    it('builds a quote consistent with the day and year histories', () => {
      const q = service.quote('AAPL')!;
      const day = service.history('AAPL', '1D').map((p) => p.price);

      expect(q.last).toBe(189.35);
      expect(q.open).toBe(day[0]);
      expect(q.change).toBeCloseTo(q.last - q.open, 2);
      expect(q.changePercent).toBeCloseTo((q.change / q.open) * 100, 1);
      expect(q.dayHigh).toBe(Math.max(...day));
      expect(q.dayLow).toBe(Math.min(...day));
      expect(q.yearHigh).toBeGreaterThanOrEqual(q.dayHigh);
      expect(q.yearLow).toBeLessThanOrEqual(q.dayLow);
      expect(q.volume).toBeGreaterThan(0);
      expect(q.asOf).toBe(NOW);
    });

    it('has no data for unknown symbols', () => {
      expect(service.history('NOPE', '1D')).toEqual([]);
      expect(service.quote('NOPE')).toBeNull();
    });
  });
});
