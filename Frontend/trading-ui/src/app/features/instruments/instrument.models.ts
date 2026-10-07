/**
 * Instrument types for search, details and charts.
 *
 * `Candle` / `CandleSeries` mirror the Fauxnance candles payload and `Quote`
 * mirrors the platform's QUOTE event (QuotePayload on the `market-data` topic),
 * so the front end can switch from fixtures to a real feed without reshaping data.
 */

export type AssetClass = 'EQUITY' | 'FX' | 'CRYPTO';

/** A tradeable instrument (mirrors the INSTRUMENTS table) */
export interface Instrument {
  id: number;
  symbol: string;
  name: string;
  assetClass: AssetClass;
  currency: string;
  tradable: boolean;
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

export interface CandleSeries {
  symbol: string;
  interval: '1d';
  currency: string;
  candles: Candle[];
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
  marketState: MarketState;
  /** Quote is past its freshness window (delayed data) */
  stale: boolean;
  /** ISO timestamp of the observation */
  quoteAsOf: string;
}

/** Where an instrument's market data comes from */
export type MarketDataSource = 'snapshot' | 'sample';

/** Daily candles only, so ranges start at one week */
export type ChartRange = '1W' | '1M' | '3M';

export const CHART_RANGES: readonly ChartRange[] = ['1W', '1M', '3M'];

export type ChartMode = 'candles' | 'line';

/**
 * Figures for the stats grid.
 * Candles never include the quote's own day and quotes carry no open/high/low,
 * so session figures describe the PREVIOUS session; price-relative figures use the quote.
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
  /** Current quote price, for positioning within the 52-week range */
  price: number;
  /** 52-week high/low from the candles, widened to include the current price */
  yearHigh: number;
  yearLow: number;
  /** Change from the first close in the chart range to the current price, in percent */
  rangeReturnPercent: number | null;
}
