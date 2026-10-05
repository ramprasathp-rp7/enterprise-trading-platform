import { Injectable } from '@angular/core';
import { ChartRange, Instrument, InstrumentQuote, PricePoint } from '../instrument.models';

/**
 * Instrument catalogue, search and SAMPLE market data.
 *
 * The Trade API has no instruments or market-data endpoint yet, so:
 * - the catalogue mirrors the INSTRUMENTS table (seed 003_instruments.sql), so every
 *   result is an instrument the order service accepts;
 * - quotes and price history are generated, deterministic sample data
 *   (same symbol + range → same numbers), clearly labelled in the UI.
 * Swap `quote()` / `history()` for real API calls when market data is available.
 */
const CATALOGUE: Instrument[] = [
  { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'ACME', name: 'Acme Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'INFY.NS', name: 'Infosys Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'RELIANCE.NS', name: 'Reliance Industries', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'TATASTEEL.BO', name: 'Tata Steel Limited', assetClass: 'EQUITY', currency: 'INR', tradable: false },
  { symbol: 'FX:EURUSD', name: 'Euro / US Dollar', assetClass: 'FX', currency: 'USD', tradable: true },
  { symbol: 'X:BTC-USD', name: 'Bitcoin / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
];

/** Sample reference prices (last traded) */
const REFERENCE_PRICE: Record<string, number> = {
  AAPL: 189.35,
  ACME: 25.5,
  'INFY.NS': 1489.5,
  'RELIANCE.NS': 2915.4,
  'TATASTEEL.BO': 141.05,
  'FX:EURUSD': 1.0842,
  'X:BTC-USD': 64250,
};

interface RangeShape {
  points: number;
  stepMs: number;
  volatility: number;
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

const RANGE_SHAPE: Record<ChartRange, RangeShape> = {
  '1D': { points: 79, stepMs: 5 * MINUTE, volatility: 0.0016 },
  '1W': { points: 41, stepMs: 4 * HOUR, volatility: 0.0045 },
  '1M': { points: 31, stepMs: DAY, volatility: 0.011 },
  '3M': { points: 91, stepMs: DAY, volatility: 0.012 },
  '1Y': { points: 253, stepMs: (365 / 252) * DAY, volatility: 0.013 },
};

@Injectable({ providedIn: 'root' })
export class InstrumentCatalogService {
  /** Fixed "now" for sample data; overridable in tests */
  now: () => Date = () => new Date();

  all(): Instrument[] {
    return [...CATALOGUE];
  }

  /** Exact lookup by symbol (case-insensitive) */
  find(symbol: string | null | undefined): Instrument | null {
    const wanted = (symbol ?? '').trim().toUpperCase();
    return CATALOGUE.find((i) => i.symbol === wanted) ?? null;
  }

  /**
   * Search by symbol or name. Ranking: exact symbol, symbol prefix,
   * name word prefix, then any substring.
   */
  search(query: string | null | undefined, limit = 8): Instrument[] {
    const q = (query ?? '').trim().toUpperCase();
    if (!q) {
      return CATALOGUE.slice(0, limit);
    }

    const scored = CATALOGUE.map((instrument) => {
      const symbol = instrument.symbol.toUpperCase();
      const bareSymbol = symbol.replace(/^(FX|X):/, '');
      const name = instrument.name.toUpperCase();
      let score = -1;
      if (symbol === q || bareSymbol === q) score = 0;
      else if (symbol.startsWith(q) || bareSymbol.startsWith(q)) score = 1;
      else if (name.split(/[\s/]+/).some((word) => word.startsWith(q))) score = 2;
      else if (symbol.includes(q) || name.includes(q)) score = 3;
      return { instrument, score };
    });

    return scored
      .filter((s) => s.score >= 0)
      .sort((a, b) => a.score - b.score || a.instrument.symbol.localeCompare(b.instrument.symbol))
      .slice(0, limit)
      .map((s) => s.instrument);
  }

  /** Display precision for an instrument's prices */
  decimals(symbol: string): number {
    const instrument = this.find(symbol);
    return instrument?.assetClass === 'FX' ? 4 : 2;
  }

  /** SAMPLE price history ending at the reference price */
  history(symbol: string, range: ChartRange): PricePoint[] {
    const instrument = this.find(symbol);
    if (!instrument) {
      return [];
    }
    const shape = RANGE_SHAPE[range];
    const random = seededRandom(`${instrument.symbol}|${range}`);
    const last = REFERENCE_PRICE[instrument.symbol] ?? 100;
    const end = this.now().getTime();

    // Walk backwards from the last price so every range ends at the same quote
    const prices = new Array<number>(shape.points);
    prices[shape.points - 1] = last;
    for (let i = shape.points - 2; i >= 0; i--) {
      const shock = gaussian(random) * shape.volatility;
      const drift = 0.0004;
      prices[i] = prices[i + 1] / (1 + drift + shock);
    }

    const decimals = this.decimals(instrument.symbol);
    return prices.map((price, i) => ({
      time: new Date(end - (shape.points - 1 - i) * shape.stepMs),
      price: round(price, decimals),
    }));
  }

  /** SAMPLE headline quote, consistent with the 1D and 1Y histories */
  quote(symbol: string): InstrumentQuote | null {
    const instrument = this.find(symbol);
    if (!instrument) {
      return null;
    }
    const day = this.history(instrument.symbol, '1D').map((p) => p.price);
    const year = this.history(instrument.symbol, '1Y').map((p) => p.price);
    const decimals = this.decimals(instrument.symbol);
    const last = day[day.length - 1];
    const open = day[0];
    const change = round(last - open, decimals);
    const random = seededRandom(`${instrument.symbol}|volume`);

    return {
      symbol: instrument.symbol,
      last,
      open,
      change,
      changePercent: round((change / open) * 100, 2),
      dayHigh: Math.max(...day),
      dayLow: Math.min(...day),
      yearHigh: Math.max(...year, ...day),
      yearLow: Math.min(...year, ...day),
      volume: Math.round(200_000 + random() * 9_800_000),
      decimals,
      asOf: this.now(),
    };
  }
}

/** Deterministic PRNG (mulberry32) seeded from a string */
function seededRandom(seed: string): () => number {
  let h = 1779033703 ^ seed.length;
  for (let i = 0; i < seed.length; i++) {
    h = Math.imul(h ^ seed.charCodeAt(i), 3432918353);
    h = (h << 13) | (h >>> 19);
  }
  let state = h >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Standard normal sample (Box–Muller) */
function gaussian(random: () => number): number {
  const u = Math.max(random(), 1e-9);
  const v = random();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}
