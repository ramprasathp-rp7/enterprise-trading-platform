import { Injectable } from '@angular/core';
import {
  Candle,
  CandleSeries,
  CandleStats,
  ChartRange,
  Instrument,
  MarketDataSource,
  MarketState,
  Quote,
} from '../instrument.models';
import { INFY_NS_DAILY } from '../data/infy-ns.candles';

/**
 * Instrument catalogue, search and market data for the front end.
 *
 * The Trade API has no instruments or market-data endpoint yet, so:
 * - the catalogue mirrors the INSTRUMENTS table (seed 003_instruments.sql), so every
 *   result is an instrument the order service accepts;
 * - INFY.NS uses a real daily-candle SNAPSHOT captured from Fauxnance;
 * - every other instrument gets deterministic SAMPLE candles in the same shape.
 * Quotes are derived from the latest candles with the fields of the platform's
 * QUOTE event (price, bid/ask, change, previous close, market state, staleness).
 * Swap `candles()` / `quote()` for API calls when a market-data endpoint exists.
 */
const CATALOGUE: Instrument[] = [
  { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'ACME', name: 'Acme Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'INFY.NS', name: 'Infosys Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'RELIANCE.NS', name: 'Reliance Industries', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'TATASTEEL.BO', name: 'Tata Steel Limited', assetClass: 'EQUITY', currency: 'INR', tradable: false },
  { symbol: 'FX:EURUSD', name: 'Euro / US Dollar', assetClass: 'FX', currency: 'USD', tradable: true },
  { symbol: 'X:BTC-USD', name: 'Bitcoin / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
  // seed 007_additional_instruments.sql: brings the catalogue to Fauxnance's 25-symbol batch limit
  { symbol: 'MSFT', name: 'Microsoft Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'GOOGL', name: 'Alphabet Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'AMZN', name: 'Amazon.com Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'NVDA', name: 'NVIDIA Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'TSLA', name: 'Tesla Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'META', name: 'Meta Platforms Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'JPM', name: 'JPMorgan Chase & Co.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'NFLX', name: 'Netflix Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'TCS.NS', name: 'Tata Consultancy Services', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'HDFCBANK.NS', name: 'HDFC Bank Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'ICICIBANK.NS', name: 'ICICI Bank Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'WIPRO.NS', name: 'Wipro Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { symbol: 'FX:GBPUSD', name: 'British Pound / US Dollar', assetClass: 'FX', currency: 'USD', tradable: true },
  { symbol: 'FX:USDJPY', name: 'US Dollar / Japanese Yen', assetClass: 'FX', currency: 'JPY', tradable: true },
  { symbol: 'FX:USDINR', name: 'US Dollar / Indian Rupee', assetClass: 'FX', currency: 'INR', tradable: true },
  { symbol: 'X:ETH-USD', name: 'Ethereum / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
  { symbol: 'X:SOL-USD', name: 'Solana / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
  { symbol: 'X:XRP-USD', name: 'XRP / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
];

/** Real candle snapshots, by symbol */
const SNAPSHOTS: Record<string, CandleSeries> = {
  'INFY.NS': INFY_NS_DAILY,
};

/** Last close of the generated sample candles */
const REFERENCE_PRICE: Record<string, number> = {
  AAPL: 189.35,
  ACME: 25.5,
  'INFY.NS': 1489.5,
  'RELIANCE.NS': 2915.4,
  'TATASTEEL.BO': 141.05,
  'FX:EURUSD': 1.0842,
  'X:BTC-USD': 64250,
  // Dummy reference prices until a live quote feed is connected
  MSFT: 415.2,
  GOOGL: 168.4,
  AMZN: 182.75,
  NVDA: 121.6,
  TSLA: 248.3,
  META: 505.1,
  JPM: 201.45,
  NFLX: 645.9,
  'TCS.NS': 3925.5,
  'HDFCBANK.NS': 1642.3,
  'ICICIBANK.NS': 1215.8,
  'WIPRO.NS': 486.25,
  'FX:GBPUSD': 1.2715,
  'FX:USDJPY': 151.42,
  'FX:USDINR': 83.47,
  'X:ETH-USD': 3150,
  'X:SOL-USD': 145.3,
  'X:XRP-USD': 0.52,
};

/** Daily volatility and typical volume for generated candles (FX has no volume) */
const SAMPLE_PROFILE: Record<Instrument['assetClass'], { volatility: number; volume: number }> = {
  EQUITY: { volatility: 0.014, volume: 6_000_000 },
  FX: { volatility: 0.004, volume: 0 },
  CRYPTO: { volatility: 0.03, volume: 25_000 },
};

/** Half the modelled bid/ask spread, relative to price */
const HALF_SPREAD: Record<Instrument['assetClass'], number> = {
  EQUITY: 0.0002,
  FX: 0.00004,
  CRYPTO: 0.0004,
};

/** A quote older than this is flagged as delayed */
const STALE_AFTER_MS = 15 * 60_000;
const SAMPLE_SESSIONS = 260;
const DAY_MS = 86_400_000;

/** Exchange time zone and sessions, in local minutes past midnight */
export interface ExchangeHours {
  timeZone: string;
  /** Short zone label shown next to times, e.g. IST */
  label: string;
  pre: number;
  open: number;
  close: number;
  post: number;
}

const NSE_HOURS: ExchangeHours = { timeZone: 'Asia/Kolkata', label: 'IST', pre: 9 * 60, open: 9 * 60 + 15, close: 15 * 60 + 30, post: 16 * 60 };
const US_HOURS: ExchangeHours = { timeZone: 'America/New_York', label: 'ET', pre: 4 * 60, open: 9 * 60 + 30, close: 16 * 60, post: 20 * 60 };

/** First day of each chart range, counted back from the latest candle */
const RANGE_START: Record<ChartRange, (end: Date) => Date> = {
  '1W': (end) => addDays(end, -7),
  '1M': (end) => addMonths(end, -1),
  '3M': (end) => addMonths(end, -3),
  '6M': (end) => addMonths(end, -6),
  '1Y': (end) => addMonths(end, -12),
};

@Injectable({ providedIn: 'root' })
export class InstrumentCatalogService {
  /** Clock for sample data, market state and staleness; overridable in tests */
  now: () => Date = () => new Date();

  private readonly sampleCache = new Map<string, CandleSeries>();

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

  /** Real snapshot or generated sample */
  dataSource(symbol: string): MarketDataSource {
    const instrument = this.find(symbol);
    return instrument && SNAPSHOTS[instrument.symbol] ? 'snapshot' : 'sample';
  }

  /** Daily candles, oldest first */
  candles(symbol: string): CandleSeries | null {
    const instrument = this.find(symbol);
    if (!instrument) {
      return null;
    }
    return SNAPSHOTS[instrument.symbol] ?? this.sampleCandles(instrument);
  }

  /** Candles inside a chart range, counted back from the latest candle */
  candlesInRange(symbol: string, range: ChartRange): Candle[] {
    const candles = this.candles(symbol)?.candles ?? [];
    if (!candles.length) {
      return [];
    }
    const from = isoDate(RANGE_START[range](parseDate(candles[candles.length - 1].date)));
    return candles.filter((c) => c.date > from);
  }

  /**
   * Figures for the stats row. Candles never include the quote's own day, and the
   * quote has no open/high/low, so session figures describe the PREVIOUS session
   * (the candle whose close is the quote's previous close); price-relative figures
   * (52-week position, range return) use the live quote price.
   */
  stats(symbol: string, range: ChartRange): CandleStats | null {
    const quote = this.quote(symbol);
    const candles = this.candles(symbol)?.candles ?? [];
    if (!quote || !candles.length) {
      return null;
    }
    const quoteDay = this.quoteDay(symbol)!;
    const history = candles.filter((c) => c.date < quoteDay);
    const previous = history[history.length - 1] ?? null;
    const yearFrom = isoDate(addMonths(parseDate(quoteDay), -12));
    const year = candles.filter((c) => c.date > yearFrom);
    const recent = history.filter((c) => !c.synthetic && c.volume !== null).slice(-30);
    const first = this.candlesInRange(symbol, range)[0];

    return {
      sessionDate: previous?.date ?? null,
      previousClose: quote.previousClose,
      previousHigh: previous?.high ?? null,
      previousLow: previous?.low ?? null,
      previousVolume: previous && !previous.synthetic ? previous.volume : null,
      averageVolume30d: recent.length ? Math.round(recent.reduce((sum, c) => sum + (c.volume ?? 0), 0) / recent.length) : null,
      price: quote.price,
      yearHigh: Math.max(quote.price, ...year.map((c) => c.high)),
      yearLow: Math.min(quote.price, ...year.map((c) => c.low)),
      rangeReturnPercent: first ? ((quote.price - first.close) / first.close) * 100 : null,
    };
  }

  /**
   * Latest quote with the QUOTE event's fields.
   * - Sample instruments: a live price for today; the previous close is the last candle.
   * - Snapshots have no quote feed, so their quote is the last candle's close (delayed),
   *   against the candle before it.
   */
  quote(symbol: string): Quote | null {
    const instrument = this.find(symbol);
    const candles = this.candles(symbol)?.candles ?? [];
    if (!instrument || !candles.length) {
      return null;
    }
    const decimals = this.decimals(instrument.symbol);
    const now = this.now();
    const isSnapshot = this.dataSource(instrument.symbol) === 'snapshot';
    const last = candles[candles.length - 1];

    const price = isSnapshot ? last.close : round(REFERENCE_PRICE[instrument.symbol] ?? last.close, decimals);
    const previousCandle = isSnapshot ? candles[candles.length - 2] : last;
    const previousClose = previousCandle?.close ?? null;
    const asOf = isSnapshot ? sessionCloseInstant(last.date, this.exchangeHours(instrument)) : now;
    const halfSpread = Math.max(price * HALF_SPREAD[instrument.assetClass], 10 ** -decimals);

    return {
      symbol: instrument.symbol,
      price,
      bid: round(price - halfSpread, decimals),
      ask: round(price + halfSpread, decimals),
      currency: instrument.currency,
      change: previousClose !== null ? round(price - previousClose, decimals) : null,
      changePercent: previousClose ? round(((price - previousClose) / previousClose) * 100, 2) : null,
      previousClose,
      marketState: isSnapshot ? 'closed' : this.marketState(instrument, now),
      stale: now.getTime() - asOf.getTime() > STALE_AFTER_MS,
      quoteAsOf: asOf.toISOString(),
    };
  }

  /** Market session for an instrument at a moment in time */
  marketState(instrument: Instrument, at: Date): MarketState {
    if (instrument.assetClass === 'CRYPTO') {
      return 'open';
    }
    if (instrument.assetClass === 'FX') {
      // Sunday 22:00 UTC to Friday 22:00 UTC
      const day = at.getUTCDay();
      const hour = at.getUTCHours();
      const closed = day === 6 || (day === 5 && hour >= 22) || (day === 0 && hour < 22);
      return closed ? 'closed' : 'open';
    }
    const hours = this.exchangeHours(instrument)!;
    const { weekday, minutes } = localClock(at, hours.timeZone);
    if (weekday === 0 || weekday === 6) {
      return 'closed';
    }
    if (minutes >= hours.open && minutes < hours.close) return 'open';
    if (minutes >= hours.pre && minutes < hours.open) return 'pre';
    if (minutes >= hours.close && minutes < hours.post) return 'post';
    return 'closed';
  }

  /** Exchange clock for equities; null for markets that trade around the clock */
  exchangeHours(instrument: Instrument): ExchangeHours | null {
    if (instrument.assetClass !== 'EQUITY') {
      return null;
    }
    return /\.(NS|BO)$/.test(instrument.symbol) ? NSE_HOURS : US_HOURS;
  }

  /** The trading day the quote belongs to: today for live quotes, the last candle for snapshots */
  private quoteDay(symbol: string): string | null {
    const candles = this.candles(symbol)?.candles ?? [];
    if (!candles.length) {
      return null;
    }
    return this.dataSource(symbol) === 'snapshot' ? candles[candles.length - 1].date : isoDate(this.now());
  }

  /**
   * Deterministic sample candles for completed sessions only (like the candles API,
   * never today). Today's move from the last close to the reference price is the quote's change.
   */
  private sampleCandles(instrument: Instrument): CandleSeries {
    const end = this.now();
    const key = `${instrument.symbol}|${isoDate(end)}`;
    const cached = this.sampleCache.get(key);
    if (cached) {
      return cached;
    }

    const allDays = instrument.assetClass === 'CRYPTO';
    const dates: string[] = [];
    let day = addDays(new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), end.getUTCDate())), -1);
    while (dates.length < SAMPLE_SESSIONS) {
      const weekday = day.getUTCDay();
      if (allDays || (weekday !== 0 && weekday !== 6)) {
        dates.unshift(isoDate(day));
      }
      day = addDays(day, -1);
    }

    const { volatility, volume } = SAMPLE_PROFILE[instrument.assetClass];
    const decimals = this.decimals(instrument.symbol);
    const random = seededRandom(`${instrument.symbol}|candles`);
    const closes = new Array<number>(dates.length);
    // Yesterday's close sits one daily move away from today's quote price
    const todayMove = gaussian(random) * volatility * 0.6;
    closes[dates.length - 1] = (REFERENCE_PRICE[instrument.symbol] ?? 100) / (1 + todayMove);
    for (let i = dates.length - 2; i >= 0; i--) {
      closes[i] = closes[i + 1] / (1 + 0.0004 + gaussian(random) * volatility);
    }

    const candles: Candle[] = dates.map((date, i) => {
      const close = closes[i];
      const open = i === 0 ? close : closes[i - 1] * (1 + gaussian(random) * volatility * 0.35);
      const high = Math.max(open, close) * (1 + Math.abs(gaussian(random)) * volatility * 0.45);
      const low = Math.min(open, close) * (1 - Math.abs(gaussian(random)) * volatility * 0.45);
      // ~2% of sessions are provider gap-fills (never the latest one)
      const synthetic = i < dates.length - 1 && random() < 0.02;
      return {
        date,
        open: round(open, decimals),
        high: round(high, decimals),
        low: round(low, decimals),
        close: round(close, decimals),
        adjclose: round(close, decimals),
        volume: synthetic || !volume ? null : Math.round(volume * (0.55 + random() * 0.9)),
        synthetic,
      };
    });

    const series: CandleSeries = { symbol: instrument.symbol, interval: '1d', currency: instrument.currency, candles };
    this.sampleCache.set(key, series);
    return series;
  }
}

function parseDate(date: string): Date {
  return new Date(`${date}T00:00:00Z`);
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * DAY_MS);
}

function addMonths(date: Date, months: number): Date {
  const d = new Date(date.getTime());
  d.setUTCMonth(d.getUTCMonth() + months);
  return d;
}

/** Weekday (0 = Sunday) and minutes past midnight in a time zone */
function localClock(at: Date, timeZone: string): { weekday: number; minutes: number } {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    weekday: 'short',
    hour: 'numeric',
    minute: 'numeric',
    hourCycle: 'h23',
  }).formatToParts(at);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(get('weekday'));
  return { weekday, minutes: Number(get('hour')) * 60 + Number(get('minute')) };
}

/** The instant a session closed, e.g. 2026-06-29 15:30 IST; end of day UTC for 24h markets */
function sessionCloseInstant(date: string, hours: ExchangeHours | null): Date {
  if (!hours) {
    return new Date(`${date}T23:59:00Z`);
  }
  // Measure the zone's UTC offset at midnight UTC that day, then place the local close time
  const midnightUtc = parseDate(date);
  const { minutes, weekday } = localClock(midnightUtc, hours.timeZone);
  const offsetMinutes = weekday === midnightUtc.getUTCDay() ? minutes : minutes - 24 * 60;
  return new Date(midnightUtc.getTime() + (hours.close - offsetMinutes) * 60_000);
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
