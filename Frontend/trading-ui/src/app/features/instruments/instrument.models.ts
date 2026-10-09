/**
 * Instrument types for search, details and charts.
 *
 * `Candle` mirrors the candle API's CandleResponse (contracts/candle-api.yaml) and `Quote`
 * mirrors the market data API's CachedQuoteResponse (contracts/market-data-api.yaml),
 * which carries the fields of the platform's QUOTE event.
 */

export type AssetClass = 'EQUITY' | 'FX' | 'CRYPTO';

/** Where an instrument trades (instruments.exchange, from the Fauxnance symbol registry) */
export type Exchange = 'NYSE' | 'NASDAQ' | 'NSE' | 'BSE' | 'FX' | 'CRYPTO';

/** A tradeable instrument (mirrors the INSTRUMENTS table) */
export interface Instrument {
  id: number;
  symbol: string;
  name: string;
  assetClass: AssetClass;
  currency: string;
  tradable: boolean;
  /** Derived from the symbol when the catalogue has none (see exchangeOf) */
  exchange?: Exchange;
}

/** One daily OHLCV bar */
export interface Candle {
  /** Trading day, YYYY-MM-DD */
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  /** Close adjusted for dividends and splits */
  adjclose: number;
  /** Shares traded; null when unknown (synthetic bars) */
  volume: number | null;
  /** True for gap-fill bars estimated by the data provider (no real trades) */
  synthetic: boolean;
}

/** Market session reported with a quote */
export type MarketState = 'open' | 'closed' | 'pre' | 'post' | 'unknown';

/** Latest quote (same fields as QuotePayload) */
export interface Quote {
  symbol: string;
  /** Last observed price (midpoint) */
  price: number;
  bid: number;
  ask: number;
  currency: string;
  /** Absolute change against previous close */
  change: number | null;
  /** Percentage points change against previous close */
  changePercent: number | null;
  previousClose: number | null;
  /** From the feed when it knows; otherwise from the exchange's trading hours */
  marketState: MarketState;
  /** The market is open and the quote is more than 15 minutes old */
  stale: boolean;
  /** How far behind the quote is, in whole minutes, when stale; otherwise null */
  delayMinutes: number | null;
  /** ISO timestamp of the observation */
  quoteAsOf: string;
}

/**
 * 'Today' plots the day's cached quotes (intraday); the others plot daily candles.
 * The backend keeps about three months of candles, so ranges stop at 3M.
 */
export type ChartRange = 'Today' | '1W' | '1M' | '3M';

export const CHART_RANGES: readonly ChartRange[] = ['Today', '1W', '1M', '3M'];

export type ChartMode = 'candles' | 'line';

/** One cached quote on the intraday chart */
export interface QuotePoint {
  /** ISO timestamp the cache received the quote */
  time: string;
  price: number;
  bid: number;
  ask: number;
}

/**
 * Figures for the stats grid, all from real data: candles from the candle API and the
 * latest cached quote. Candles never include the quote's own day and quotes carry no
 * open/high/low, so session figures describe the PREVIOUS session; price-relative
 * figures use the quote (or the last close when no quote is cached yet).
 */
export interface CandleStats {
  /** Date of the previous session (YYYY-MM-DD) */
  sessionDate: string | null;
  previousClose: number | null;
  previousHigh: number | null;
  previousLow: number | null;
  /** Previous session's volume; null when unknown or the bar was synthetic */
  previousVolume: number | null;
  /** Average volume over the last 30 real sessions */
  averageVolume30d: number | null;
  /** Current quote price (last close if no quote), for positioning within the high/low range */
  price: number;
  /** High/low over the candle history (up to 52 weeks), widened to include the current price */
  yearHigh: number;
  yearLow: number;
  /** First session of the candle history the high/low covers (YYYY-MM-DD) */
  historyFrom: string;
  /** Change from the first close in the chart range to the current price, in percent */
  rangeReturnPercent: number | null;
}
