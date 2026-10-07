import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { QuotesService, CachedQuoteResponse } from '../../../api/generated/quotes';

/**
 * Intraday quote service
 *
 * Fetches real-time and cached quote history for today (every 5 minutes from MarketDataCache).
 * Returns time-series data (price points over time) suitable for line charts.
 *
 * Calls:
 * - GET /api/v1/market-data/quotes (today's quotes for a symbol)
 */
export interface QuotePoint {
  timestamp: string;  // ISO 8601 timestamp from receivedAt
  price: number;
  bid: number;
  ask: number;
}

@Injectable({
  providedIn: 'root',
})
export class QuoteIntraDayService {
  constructor(private quotesApi: QuotesService) {}

  /**
   * Get today's quote history for an instrument
   * Returns time-series points (price, bid, ask) received throughout the day
   */
  getQuotesForToday(symbol: string): Observable<QuotePoint[]> {
    return this.quotesApi
      .getQuotesForDay(undefined, symbol.toUpperCase(), undefined)
      .pipe(
        map((response) => this.mapQuotesToPoints(response.rows || [])),
        catchError(() => of([]))  // No data: return empty array
      );
  }

  // ===== Private helpers =====

  private mapQuotesToPoints(quotes: CachedQuoteResponse[]): QuotePoint[] {
    return quotes.map((q) => ({
      timestamp: q.receivedAt ?? q.quoteAsOf ?? new Date().toISOString(),
      price: Number(q.price),
      bid: Number(q.bid),
      ask: Number(q.ask),
    }));
  }
}
