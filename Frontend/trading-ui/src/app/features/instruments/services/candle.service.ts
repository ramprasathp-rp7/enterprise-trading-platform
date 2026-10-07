import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  CandleHistoryResponse,
  CandleResponse,
  CandlesService,
} from '../../../api/generated/candle';
import { Candle, ChartRange } from '../instrument.models';

/**
 * Candle chart data service
 *
 * Wraps the auto-generated CandlesService to provide business logic:
 * - Date range mapping for chart range selections (1W/1M/3M)
 * - Conversion from API DTOs to frontend Candle model
 * - Error handling (404 for missing data returns empty array)
 *
 * Calls:
 * - GET /api/v1/instruments/{instrumentId}/candles
 * - GET /api/v1/instruments/{instrumentId}/candles/latest
 * - GET /api/v1/instruments/{instrumentId}/candles/{date}
 */
@Injectable({
  providedIn: 'root',
})
export class CandleService {
  constructor(private candlesApi: CandlesService) {}

  /**
   * Get candles for a chart range (1W/1M/3M)
   * Maps the range to from/to dates and fetches daily candles (interval=1d)
   */
  getCandlesForRange(instrumentId: number, range: ChartRange): Observable<Candle[]> {
    const today = new Date();
    today.setUTCHours(0, 0, 0, 0);

    const { from, to } = this.getDateRangeForChartRange(range, today);
    const fromStr = this.formatDate(from);
    const toStr = this.formatDate(to);

    return this.candlesApi
      .getCandlesByInstrument(instrumentId, fromStr, toStr, undefined, 500, 0)
      .pipe(
        map((response) => this.mapCandlesToFrontendModel(response.candles)),
        catchError(() => of([])) // 404 or other error: return empty array
      );
  }

  /**
   * Get the N most recent candles for an instrument
   */
  getLatestCandles(instrumentId: number, count: number): Observable<Candle[]> {
    return this.candlesApi.getLatestCandles(instrumentId, Math.min(count, 500)).pipe(
      map((response) => this.mapCandlesToFrontendModel(response.candles)),
      catchError(() => of([]))
    );
  }

  /**
   * Get a single candle for a specific date
   */
  getCandleByDate(instrumentId: number, date: Date): Observable<Candle | null> {
    const dateStr = this.formatDate(date);
    return this.candlesApi.getCandleByDate(instrumentId, dateStr).pipe(
      map((response) => this.mapDtoToCandle(response)),
      catchError(() => of(null))
    );
  }

  // ===== Private helpers =====

  private getDateRangeForChartRange(
    range: ChartRange,
    today: Date
  ): { from: Date; to: Date } {
    const from = new Date(today);

    switch (range) {
      case '1W':
        from.setUTCDate(from.getUTCDate() - 7);
        break;
      case '1M':
        from.setUTCDate(from.getUTCDate() - 30);
        break;
      case '3M':
        // Backend max date range is 90 days; use 89 to stay within limit
        from.setUTCDate(from.getUTCDate() - 89);
        break;
      default:
        const _exhaustive: never = range;
        return _exhaustive;
    }

    return { from, to: today };
  }

  private formatDate(date: Date): string {
    const year = date.getUTCFullYear();
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const day = String(date.getUTCDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private mapCandlesToFrontendModel(dtos: CandleResponse[]): Candle[] {
    return dtos.map((dto) => this.mapDtoToCandle(dto));
  }

  private mapDtoToCandle(dto: CandleResponse): Candle {
    return {
      date: dto.date,
      open: Number(dto.open),
      high: Number(dto.high),
      low: Number(dto.low),
      close: Number(dto.close),
      adjclose: Number(dto.adjClose),
      volume: dto.volume,
      synthetic: dto.synthetic,
    };
  }
}
