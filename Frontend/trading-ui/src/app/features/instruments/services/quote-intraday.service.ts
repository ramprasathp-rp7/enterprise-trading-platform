import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CachedQuoteResponse, MarketDataService } from '../../../api/generated/market-data';
import { QuotePoint } from '../instrument.models';

/**
 * Today's quote history for one symbol, from the order service's quote cache via
 * GET /api/v1/market-data/quotes (contracts/market-data-api.yaml). The cache is refreshed
 * from Fauxnance every five minutes (or by the market-data topic), so this costs no quota.
 * Errors are passed on so callers can show them.
 */
@Injectable({ providedIn: 'root' })
export class QuoteIntraDayService {
  private readonly api = inject(MarketDataService);

  /** Quotes the cache received today (UTC day), oldest first */
  getQuotesForToday(symbol: string): Observable<QuotePoint[]> {
    return this.api
      .getQuotesForDay(undefined, symbol.trim().toUpperCase())
      .pipe(map((response) => toPoints(response?.quotes)));
  }
}

function toPoints(rows: CachedQuoteResponse[] | null | undefined): QuotePoint[] {
  return (rows ?? [])
    .map((q) => ({
      time: q.receivedAt ?? q.quoteAsOf ?? '',
      price: Number(q.price),
      bid: Number(q.bid),
      ask: Number(q.ask),
    }))
    .filter((p) => p.time && Number.isFinite(p.price))
    .sort((a, b) => a.time.localeCompare(b.time));
}
