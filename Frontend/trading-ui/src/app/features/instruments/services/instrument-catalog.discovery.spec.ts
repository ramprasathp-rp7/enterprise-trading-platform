import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { InstrumentCatalogService, exchangeOf, inMarket, marketLabel } from './instrument-catalog.service';
import { InstrumentResponse, InstrumentsService } from '../../../api/generated/instruments';
import { Instrument } from '../instrument.models';

const asResponse = (i: Instrument): InstrumentResponse => ({
  id: i.id,
  symbol: i.symbol,
  name: i.name,
  assetClass: i.assetClass,
  currency: i.currency,
  tradable: i.tradable,
  exchange: exchangeOf(i),
});

describe('InstrumentCatalogService: markets, loading and symbol discovery', () => {
  let service: InstrumentCatalogService;
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let seeded: Instrument[];

  beforeEach(() => {
    api = { listInstruments: vi.fn(), discoverInstrument: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: InstrumentsService, useValue: api }] });
    service = TestBed.inject(InstrumentCatalogService);
    seeded = service.all();
  });

  afterEach(() => {
    // The catalogue is module state: put the seeded instruments back for the next test
    api['listInstruments'].mockReturnValue(of(seeded.map(asResponse)));
    service.load(true);
  });

  describe('markets', () => {
    it('knows each seeded instrument’s exchange', () => {
      expect(exchangeOf({ symbol: 'AAPL' })).toBe('NASDAQ');
      expect(exchangeOf({ symbol: 'JPM' })).toBe('NYSE');
      expect(exchangeOf({ symbol: 'INFY.NS' })).toBe('NSE');
      expect(exchangeOf({ symbol: 'TATASTEEL.BO' })).toBe('BSE');
      expect(exchangeOf({ symbol: 'FX:EURUSD' })).toBe('FX');
      expect(exchangeOf({ symbol: 'X:BTC-USD' })).toBe('CRYPTO');
      expect(exchangeOf({ symbol: 'SHOP', exchange: 'NYSE' })).toBe('NYSE');
    });

    it('groups exchanges into the market preferences', () => {
      expect(inMarket({ symbol: 'AAPL' }, 'USA')).toBe(true);
      expect(inMarket({ symbol: 'JPM' }, 'USA')).toBe(true);
      expect(inMarket({ symbol: 'INFY.NS' }, 'USA')).toBe(false);
      expect(inMarket({ symbol: 'INFY.NS' }, 'NSE')).toBe(true);
      expect(inMarket({ symbol: 'TATASTEEL.BO' }, 'NSE')).toBe(false);
      expect(inMarket({ symbol: 'FX:EURUSD' }, 'USA')).toBe(false);
      expect(inMarket({ symbol: 'FX:EURUSD' }, 'ALL')).toBe(true);
      expect(marketLabel('USA')).toContain('NYSE');
      expect(marketLabel('ALL')).toBe('all markets');
    });

    it('limits search to a market', () => {
      expect(service.search('', 50, 'NSE').map((i) => i.symbol)).toEqual(['INFY.NS', 'RELIANCE.NS', 'TCS.NS', 'HDFCBANK.NS', 'ICICIBANK.NS', 'WIPRO.NS']);
      expect(service.search('tata', 8, 'USA')).toEqual([]);
      expect(service.search('tata', 8, 'ALL').map((i) => i.symbol)).toContain('TCS.NS');
      expect(service.search('', 50, 'USA').every((i) => ['NYSE', 'NASDAQ'].includes(exchangeOf(i)))).toBe(true);
    });
  });

  describe('loading from the instruments API', () => {
    it('replaces the seeded catalogue, discovered symbols included', () => {
      const before = service.version();
      api['listInstruments'].mockReturnValue(
        of([...seeded.map(asResponse), { id: 26, symbol: 'SHOP', name: 'Shopify Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true, exchange: 'NYSE' }]),
      );

      service.load();

      expect(service.isLoaded()).toBe(true);
      expect(service.version()).toBeGreaterThan(before);
      expect(service.find('shop')).toMatchObject({ id: 26, name: 'Shopify Inc.', exchange: 'NYSE' });
      expect(service.all().length).toBe(seeded.length + 1);
    });

    it('loads once unless forced', () => {
      api['listInstruments'].mockReturnValue(of(seeded.map(asResponse)));
      service.load();
      service.load();
      expect(api['listInstruments']).toHaveBeenCalledTimes(1);
    });

    it('keeps the seeded catalogue when the API fails', () => {
      api['listInstruments'].mockReturnValue(throwError(() => new Error('down')));

      service.load();

      expect(service.isLoaded()).toBe(false);
      expect(service.all().length).toBe(seeded.length);
    });
  });

  describe('symbol discovery', () => {
    it('adds a discovered symbol to the catalogue', async () => {
      api['discoverInstrument'].mockReturnValue(
        of({ instrument: { id: 26, symbol: 'SHOP', name: 'Shopify Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true, exchange: 'NYSE' }, created: true }),
      );

      const result = await firstValueFrom(service.discover('  shop '));

      expect(api['discoverInstrument']).toHaveBeenCalledWith({ symbol: 'shop' });
      expect(result.created).toBe(true);
      expect(service.find('SHOP')?.exchange).toBe('NYSE');
      expect(service.search('shopify').map((i) => i.symbol)).toEqual(['SHOP']);
    });

    it('updates an already-listed instrument instead of duplicating it', async () => {
      api['discoverInstrument'].mockReturnValue(of({ instrument: asResponse(seeded[1]), created: false }));

      await firstValueFrom(service.discover('AAPL'));

      expect(service.all().filter((i) => i.symbol === 'AAPL').length).toBe(1);
    });
  });
});
