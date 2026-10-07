import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { CandlesService } from '../../../api/generated/candle';
import { CandleService } from './candle.service';

describe('CandleService', () => {
  let service: CandleService;
  let api: Record<string, ReturnType<typeof vi.fn>>;

  const row = (date: string, close: number, extra: Record<string, unknown> = {}) => ({
    date, open: close - 1, high: close + 1, low: close - 2, close, adjclose: close - 0.5, volume: 100, synthetic: false, ...extra,
  });

  beforeEach(() => {
    api = {
      getCandlesByInstrument: vi.fn(() => of({ symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', candles: [row('2026-10-02', 11), row('2026-10-01', 10)] })),
      getLatestCandles: vi.fn(),
      getCandleByDate: vi.fn(() => of(row('2026-10-01', 10))),
    };
    TestBed.configureTestingModule({ providers: [{ provide: CandlesService, useValue: api }] });
    service = TestBed.inject(CandleService);
    service.now = () => new Date('2026-10-05T15:00:00Z');
  });

  it('requests a year of daily candles by instrument id and returns them oldest first', async () => {
    const candles = await firstValueFrom(service.getHistory(2));

    // interval left to the API default (one bar per day); the order service rejects '1d'
    expect(api['getCandlesByInstrument']).toHaveBeenCalledWith(2, '2025-10-05', '2026-10-05', undefined, 500, 0);
    expect(candles.map((c) => c.date)).toEqual(['2026-10-01', '2026-10-02']);
    expect(candles[0]).toEqual({ date: '2026-10-01', open: 9, high: 11, low: 8, close: 10, adjclose: 9.5, volume: 100, synthetic: false });
  });

  it('reads adjClose from older backends and falls back to close, dropping malformed rows', async () => {
    api['getCandlesByInstrument'].mockReturnValue(of({
      candles: [
        row('2026-10-01', 10, { adjclose: undefined, adjClose: 9.9 }),
        row('2026-10-02', 11, { adjclose: null, volume: null, synthetic: true }),
        row('2026-10-03', Number.NaN),
      ],
    }));

    const candles = await firstValueFrom(service.getHistory(2));

    expect(candles.map((c) => c.adjclose)).toEqual([9.9, 11]);
    expect(candles[1].volume).toBeNull();
    expect(candles[1].synthetic).toBe(true);
  });

  it('passes errors on to the caller', async () => {
    api['getCandlesByInstrument'].mockReturnValue(throwError(() => new Error('boom')));

    await expect(firstValueFrom(service.getHistory(2))).rejects.toThrow('boom');
  });

  it('accepts the latest candles as an array or wrapped in a history response', async () => {
    api['getLatestCandles'].mockReturnValueOnce(of([row('2026-10-02', 11)]));
    expect((await firstValueFrom(service.getLatestCandles(2, 1))).length).toBe(1);

    api['getLatestCandles'].mockReturnValueOnce(of({ candles: [row('2026-10-02', 11), row('2026-10-01', 10)] }));
    expect((await firstValueFrom(service.getLatestCandles(2, 2))).map((c) => c.date)).toEqual(['2026-10-01', '2026-10-02']);
  });

  it('maps a single day', async () => {
    expect((await firstValueFrom(service.getCandleByDate(2, '2026-10-01'))).close).toBe(10);
  });
});
