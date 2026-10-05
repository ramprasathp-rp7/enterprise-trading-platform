/**
 * Instrument types for search, details and charts.
 */

export type AssetClass = 'EQUITY' | 'FX' | 'CRYPTO';

/** A tradeable instrument (mirrors the INSTRUMENTS table) */
export interface Instrument {
  symbol: string;
  name: string;
  assetClass: AssetClass;
  currency: string;
  tradable: boolean;
}

export type ChartRange = '1D' | '1W' | '1M' | '3M' | '1Y';

export const CHART_RANGES: readonly ChartRange[] = ['1D', '1W', '1M', '3M', '1Y'];

export interface PricePoint {
  time: Date;
  price: number;
}

/** Headline figures shown on the instrument panel */
export interface InstrumentQuote {
  symbol: string;
  last: number;
  change: number;
  changePercent: number;
  open: number;
  dayHigh: number;
  dayLow: number;
  yearHigh: number;
  yearLow: number;
  volume: number;
  /** Decimal places used to display prices for this instrument */
  decimals: number;
  asOf: Date;
}
