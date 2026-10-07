import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { MarketDataService } from '../../../api/generated/market-data';
import { QuoteIntraDayService } from './quote-intraday.service';

describe('QuoteIntraDayService', () => {
  let service: QuoteIntraDayService;
  let getQuotesForDay: ReturnType<typeof vi.fn>;

  const quote = (receivedAt: string, price: number) => ({
    symbol: 'INFY.NS', price, bid: price - 0.1, ask: price + 0.1, currency: 'INR', quoteAsOf: receivedAt, receivedAt,
  });

  beforeEach(() => {
    getQuotesForDay = vi.fn(() =>
      of({ date: '2026-10-05', symbol: 'INFY.NS', count: 2, quotes: [quote('2026-10-05T05:05:00Z', 1501), quote('2026-10-05T05:00:00Z', 1500)] }),
    );
    TestBed.configureTestingModule({ providers: [{ provide: MarketDataService, useValue: { getQuotesForDay } }] });
    service = TestBed.inject(QuoteIntraDayService);
  });

  it("asks the market data API for today's quotes of the symbol and returns them oldest first", async () => {
    const points = await firstValueFrom(service.getQuotesForToday(' infy.ns '));

    expect(getQuotesForDay).toHaveBeenCalledWith(undefined, 'INFY.NS');
    expect(points).toEqual([
      { time: '2026-10-05T05:00:00Z', price: 1500, bid: 1499.9, ask: 1500.1 },
      { time: '2026-10-05T05:05:00Z', price: 1501, bid: 1500.9, ask: 1501.1 },
    ]);
  });

  it('returns no points for an empty day', async () => {
    getQuotesForDay.mockReturnValue(of({ date: '2026-10-05', symbol: 'INFY.NS', count: 0, quotes: [] }));

    expect(await firstValueFrom(service.getQuotesForToday('INFY.NS'))).toEqual([]);
  });
});
