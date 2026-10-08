import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CandlesService, CandleResponse, CandleHistoryResponse } from '../../../api/generated/candle';
import { Candle } from '../instrument.models';

/** Days of candle history requested for the chart and the high/low stats (52 weeks) */
export const HISTORY_DAYS = 365;
/** The candle API's page size cap */
const MAX_CANDLES = 500;
const DAY_MS = 86_400_000;

/**
 * Daily candles from the candle API (contracts/candle-api.yaml), served from the order
 * service's candle store (synced from Fauxnance). Responses are mapped to the `Candle`
 * domain model, oldest first. Errors are passed on so callers can show them.
 */
@Injectable({ providedIn: 'root' })
export class CandleService {
  private readonly candlesApi = inject(CandlesService);

  /** Clock for the requested date window; overridable in tests */
  now: () => Date = () => new Date();

  /**
   * Up to a year of daily candles ending today. One request covers every chart range
   * and the stats, so switching range never refetches.
   */
  getHistory(instrumentId: number, days = HISTORY_DAYS): Observable<Candle[]> {
    const to = this.now();
    const from = new Date(to.getTime() - days * DAY_MS);
    // interval is left to the API default: one bar per trading day
    return this.candlesApi
      .getCandlesByInstrument(instrumentId, isoDate(from), isoDate(to), undefined, MAX_CANDLES, 0)
      .pipe(map((response) => toCandles(response?.candles)));
  }

  /** The `count` latest candles, oldest first */
  getLatestCandles(instrumentId: number, count = 20): Observable<Candle[]> {
    return this.candlesApi.getLatestCandles(instrumentId, count).pipe(
      // The API documents an array, but the order service wraps it in a CandleHistoryResponse
      map((body: CandleResponse[] | CandleHistoryResponse) => toCandles(Array.isArray(body) ? body : body?.candles)),
    );
  }

  /** The candle for one trading day (YYYY-MM-DD) */
  getCandleByDate(instrumentId: number, date: string): Observable<Candle> {
    return this.candlesApi.getCandleByDate(instrumentId, date).pipe(map(toCandle));
  }
}

function toCandles(rows: CandleResponse[] | null | undefined): Candle[] {
  return (rows ?? [])
    .map(toCandle)
    .filter((c) => c.date && [c.open, c.high, c.low, c.close].every(Number.isFinite))
    .sort((a, b) => a.date.localeCompare(b.date));
}

function toCandle(row: CandleResponse): Candle {
  const close = Number(row.close);
  const adjclose = row.adjclose ?? (row as { adjClose?: number }).adjClose;
  return {
    date: String(row.date ?? '').slice(0, 10),
    open: Number(row.open),
    high: Number(row.high),
    low: Number(row.low),
    close,
    adjclose: adjclose === null || adjclose === undefined ? close : Number(adjclose),
    volume: row.volume ?? null,
    synthetic: row.synthetic ?? false,
  };
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}
