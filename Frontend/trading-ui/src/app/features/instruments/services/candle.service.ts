import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { CandlesService, CandleResponse } from '../../../api/generated/candle';
import { Candle, ChartRange } from '../instrument.models';

/**
 * Candle data service wrapper
 *
 * Fetches daily candlestick data from the backend API.
 * Maps date ranges to API date parameters.
 * Converts CandleResponse DTO to Candle domain model.
 *
 * Date range mapping:
 * - '1W' = 7 days ago
 * - '1M' = 30 days ago
 * - '3M' = 89 days ago (backend max is 90)
 * - 'Today' = same day only (for intraday quotes)
 */
@Injectable({
  providedIn: 'root',
})
export class CandleService {
  constructor(private candlesApi: CandlesService) {}

  /**
   * Get candles for an instrument within a date range
   * @param instrumentId Backend instrument ID
   * @param range Chart range (1W, 1M, 3M, Today)
   * @returns Observable<Candle[]> sorted oldest to newest
   */
  getCandlesForRange(instrumentId: number, range: ChartRange): Observable<Candle[]> {
    const today = new Date();
    const { fromDate, toDate } = this.calculateDateRange(today, range);

    return this.candlesApi
      .getCandlesByInstrument(
        instrumentId,
        fromDate,
        toDate,
        undefined, // interval
        500, // limit
        0 // offset
      )
      .pipe(
        map((response) => this.mapCandlesToDomain(response.candles || [])),
        catchError(() => of([]))
      );
  }

  /**
   * Get the N latest candles for an instrument
   * @param instrumentId Backend instrument ID
   * @param count Number of candles (max 500)
   * @returns Observable<Candle[]>
   */
  getLatestCandles(instrumentId: number, count: number = 20): Observable<Candle[]> {
    return this.candlesApi.getLatestCandles(instrumentId, count).pipe(
      map((candles) => this.mapCandlesToDomain(candles || [])),
      catchError(() => of([]))
    );
  }

  /**
   * Get candle for a specific date
   * @param instrumentId Backend instrument ID
   * @param date Trading date (YYYY-MM-DD)
   * @returns Observable<Candle | null>
   */
  getCandleByDate(instrumentId: number, date: string): Observable<Candle | null> {
    return this.candlesApi.getCandleByDate(instrumentId, date).pipe(
      map((response) => this.mapCandle(response)),
      catchError(() => of(null))
    );
  }

  // ===== Private helpers =====

  private calculateDateRange(today: Date, range: ChartRange): { fromDate: string; toDate: string } {
    const toDate = this.toDateString(today);
    let fromDate: string;

    switch (range) {
      case 'Today':
        fromDate = toDate;
        break;
      case '1W':
        fromDate = this.toDateString(new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000));
        break;
      case '1M':
        fromDate = this.toDateString(new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000));
        break;
      case '3M':
        // Use 89 days to stay within backend's 90-day max
        fromDate = this.toDateString(new Date(today.getTime() - 89 * 24 * 60 * 60 * 1000));
        break;
      default:
        fromDate = toDate;
    }

    return { fromDate, toDate };
  }

  private toDateString(date: Date): string {
    return date.toISOString().slice(0, 10);
  }

  private mapCandlesToDomain(responses: CandleResponse[]): Candle[] {
    return responses.map((r) => this.mapCandle(r));
  }

  private mapCandle(response: CandleResponse): Candle {
    return {
      date: response.date || '',
      open: Number(response.open),
      high: Number(response.high),
      low: Number(response.low),
      close: Number(response.close),
      adjclose: Number(response.adjclose),
      volume: response.volume ?? null,
      synthetic: response.synthetic ?? false,
    };
  }
}
