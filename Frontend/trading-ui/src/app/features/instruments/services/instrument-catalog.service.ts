import { Injectable } from '@angular/core';
import { CachedQuoteResponse } from '../../../api/generated/market-data';
import { Candle, CandleStats, ChartRange, Instrument, MarketState, Quote } from '../instrument.models';

/**
 * Instrument catalogue and search, plus the pure helpers that turn real market data into
 * what the instrument panel shows:
 * - the catalogue mirrors the INSTRUMENTS table (seeds 003 and 007), in id order, so every
 *   result is an instrument the order service accepts and its id addresses the candle API;
 * - quotes come from the market data API's quote cache (`toQuote`);
 * - candles come from the candle API (`candlesInRange`, `stats`).
 * Nothing here generates prices.
 */
const CATALOGUE: Instrument[] = [
  { id: 1, symbol: 'ACME', name: 'Acme Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 2, symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 3, symbol: 'INFY.NS', name: 'Infosys Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { id: 4, symbol: 'RELIANCE.NS', name: 'Reliance Industries', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { id: 5, symbol: 'TATASTEEL.BO', name: 'Tata Steel Limited', assetClass: 'EQUITY', currency: 'INR', tradable: false },
  { id: 6, symbol: 'FX:EURUSD', name: 'Euro / US Dollar', assetClass: 'FX', currency: 'USD', tradable: true },
  { id: 7, symbol: 'X:BTC-USD', name: 'Bitcoin / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
  { id: 8, symbol: 'MSFT', name: 'Microsoft Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 9, symbol: 'GOOGL', name: 'Alphabet Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 10, symbol: 'AMZN', name: 'Amazon.com Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 11, symbol: 'NVDA', name: 'NVIDIA Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 12, symbol: 'TSLA', name: 'Tesla Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 13, symbol: 'META', name: 'Meta Platforms Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 14, symbol: 'JPM', name: 'JPMorgan Chase & Co.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 15, symbol: 'NFLX', name: 'Netflix Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 16, symbol: 'TCS.NS', name: 'Tata Consultancy Services', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { id: 17, symbol: 'HDFCBANK.NS', name: 'HDFC Bank Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { id: 18, symbol: 'ICICIBANK.NS', name: 'ICICI Bank Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { id: 19, symbol: 'WIPRO.NS', name: 'Wipro Limited', assetClass: 'EQUITY', currency: 'INR', tradable: true },
  { id: 20, symbol: 'FX:GBPUSD', name: 'British Pound / US Dollar', assetClass: 'FX', currency: 'USD', tradable: true },
  { id: 21, symbol: 'FX:USDJPY', name: 'US Dollar / Japanese Yen', assetClass: 'FX', currency: 'JPY', tradable: true },
  { id: 22, symbol: 'FX:USDINR', name: 'US Dollar / Indian Rupee', assetClass: 'FX', currency: 'INR', tradable: true },
  { id: 23, symbol: 'X:ETH-USD', name: 'Ethereum / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
  { id: 24, symbol: 'X:SOL-USD', name: 'Solana / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
  { id: 25, symbol: 'X:XRP-USD', name: 'XRP / US Dollar', assetClass: 'CRYPTO', currency: 'USD', tradable: true },
];

/** States worth trusting from the feed; 'unknown' falls back to the exchange clock */
const KNOWN_MARKET_STATES: readonly MarketState[] = ['open', 'closed', 'pre', 'post'];
/** While the market is open, a quote older than this is shown as delayed */
const STALE_AFTER_MS = 15 * 60_000;
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

/** First day of each candle range, counted back from the latest candle ('Today' plots quotes) */
const RANGE_START: Record<Exclude<ChartRange, 'Today'>, (end: Date) => Date> = {
  '1W': (end) => addDays(end, -7),
  '1M': (end) => addMonths(end, -1),
  '3M': (end) => addMonths(end, -3),
};

@Injectable({ providedIn: 'root' })
export class InstrumentCatalogService {
  /** Clock for market state and the stats' trading day; overridable in tests */
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

  /** Candles inside a chart range, counted back from the latest candle */
  candlesInRange(candles: readonly Candle[], range: ChartRange): Candle[] {
    if (!candles.length || range === 'Today') {
      return [];
    }
    const from = isoDate(RANGE_START[range](parseDate(candles[candles.length - 1].date)));
    return candles.filter((c) => c.date > from);
  }

  /**
   * A cached quote as the panel's `Quote`. Fauxnance-refreshed quotes often carry no
   * previous close or change, so the previous close falls back to the last candle before
   * the quote's trading day.
   *
   * Fauxnance reports `marketState: "unknown"` and sets `stale` whenever it serves from its
   * own cache, so neither is shown as-is: an unknown or missing state comes from the
   * exchange clock, and a quote counts as delayed only while the market is open and the
   * quote is over 15 minutes old (an old price while the market is closed is expected).
   */
  toQuote(instrument: Instrument, cached: CachedQuoteResponse, candles: readonly Candle[]): Quote {
    const decimals = this.decimals(instrument.symbol);
    const asOf = cached.quoteAsOf ?? cached.receivedAt;
    const price = Number(cached.price);
    const previousClose = finite(cached.previousClose) ?? this.previousSession(instrument, candles, asOf)?.close ?? null;
    const change = finite(cached.change) ?? (previousClose !== null ? round(price - previousClose, decimals) : null);
    const changePercent =
      finite(cached.changePercent) ?? (previousClose ? round(((price - previousClose) / previousClose) * 100, 2) : null);
    const now = this.now();
    const fed = (cached.marketState ?? '').toLowerCase() as MarketState;
    const marketState = KNOWN_MARKET_STATES.includes(fed) ? fed : this.marketState(instrument, now);
    const ageMs = now.getTime() - Date.parse(asOf);
    const stale = marketState === 'open' && ageMs > STALE_AFTER_MS;

    return {
      symbol: instrument.symbol,
      price,
      bid: Number(cached.bid),
      ask: Number(cached.ask),
      currency: cached.currency || instrument.currency,
      change,
      changePercent,
      previousClose,
      marketState,
      stale,
      delayMinutes: stale ? Math.floor(ageMs / 60_000) : null,
      quoteAsOf: asOf,
    };
  }

  /**
   * Figures for the stats row, from the candle history and (when cached) the live quote.
   * Candles never include the quote's own day, and the quote has no open/high/low, so
   * session figures describe the PREVIOUS session; price-relative figures (high/low
   * position, range return) use the quote price, or the last close without a quote.
   */
  stats(instrument: Instrument, candles: readonly Candle[], quote: Quote | null, range: ChartRange): CandleStats | null {
    if (!candles.length) {
      return null;
    }
    const previous = this.previousSession(instrument, candles, quote?.quoteAsOf ?? this.now().toISOString());
    const history = previous ? candles.filter((c) => c.date <= previous.date) : candles;
    const recent = history.filter((c) => !c.synthetic && c.volume !== null).slice(-30);
    const price = quote?.price ?? candles[candles.length - 1].close;
    const first = this.candlesInRange(candles, range)[0];

    return {
      sessionDate: previous?.date ?? null,
      previousClose: quote?.previousClose ?? previous?.close ?? null,
      previousHigh: previous?.high ?? null,
      previousLow: previous?.low ?? null,
      previousVolume: previous && !previous.synthetic ? previous.volume : null,
      averageVolume30d: recent.length ? Math.round(recent.reduce((sum, c) => sum + (c.volume ?? 0), 0) / recent.length) : null,
      price,
      yearHigh: Math.max(price, ...candles.map((c) => c.high)),
      yearLow: Math.min(price, ...candles.map((c) => c.low)),
      historyFrom: candles[0].date,
      rangeReturnPercent: first && first.close ? ((price - first.close) / first.close) * 100 : null,
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

  /** The trading day (YYYY-MM-DD) an instant falls on, in the exchange's time zone (UTC for 24h markets) */
  tradingDay(instrument: Instrument, at: string | Date): string {
    const timeZone = this.exchangeHours(instrument)?.timeZone ?? 'UTC';
    return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(at));
  }

  /** The last candle before the trading day of `asOf` */
  private previousSession(instrument: Instrument, candles: readonly Candle[], asOf: string): Candle | null {
    const day = this.tradingDay(instrument, asOf);
    for (let i = candles.length - 1; i >= 0; i--) {
      if (candles[i].date < day) {
        return candles[i];
      }
    }
    return null;
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

function finite(value: number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}
