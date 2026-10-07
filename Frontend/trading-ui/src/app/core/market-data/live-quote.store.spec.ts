import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { CachedQuoteResponse, MarketDataService } from '../../api/generated/market-data';
import { LIVE_QUOTE_TTL_MS, LiveQuoteStore } from './live-quote.store';

const quote = (symbol: string, price: number): CachedQuoteResponse => ({
  symbol,
  price,
  bid: price - 0.2,
  ask: price + 0.2,
  currency: 'INR',
  changePercent: -0.42,
  stale: false,
  receivedAt: '2026-10-06T09:05:00Z',
});

describe('LiveQuoteStore', () => {
  let api: { getLatestQuote: ReturnType<typeof vi.fn> };
  let store: LiveQuoteStore;

  beforeEach(() => {
    api = { getLatestQuote: vi.fn((symbol: string) => of(quote(symbol, 1006.35))) };
    TestBed.configureTestingModule({ providers: [{ provide: MarketDataService, useValue: api }] });
    store = TestBed.inject(LiveQuoteStore);
  });

  afterEach(() => vi.useRealTimers());

  it('fetches the latest quote per symbol through the generated client', () => {
    store.refresh(['INFY.NS', 'RELIANCE.NS']);

    expect(api.getLatestQuote).toHaveBeenCalledWith('INFY.NS');
    expect(api.getLatestQuote).toHaveBeenCalledWith('RELIANCE.NS');
    expect(store.entry('INFY.NS')?.status).toBe('ready');
    expect(store.quote('INFY.NS')?.price).toBe(1006.35);
  });

  it('normalises symbols and fetches duplicates once', () => {
    store.refresh(['infy.ns', ' INFY.NS ']);

    expect(api.getLatestQuote).toHaveBeenCalledTimes(1);
    expect(api.getLatestQuote).toHaveBeenCalledWith('INFY.NS');
    expect(store.quote('Infy.NS')?.symbol).toBe('INFY.NS');
  });

  it('reuses a quote within the TTL and refetches after it or when forced', () => {
    vi.useFakeTimers();
    store.refresh(['INFY.NS']);
    store.refresh(['INFY.NS']);
    expect(api.getLatestQuote).toHaveBeenCalledTimes(1);

    store.refresh(['INFY.NS'], true);
    expect(api.getLatestQuote).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(LIVE_QUOTE_TTL_MS);
    store.refresh(['INFY.NS']);
    expect(api.getLatestQuote).toHaveBeenCalledTimes(3);
  });

  it('refreshes every five minutes even when the previous response arrived a little late', () => {
    vi.useFakeTimers();
    expect(LIVE_QUOTE_TTL_MS).toBe(5 * 60_000);

    vi.advanceTimersByTime(2_000); // response landed 2s after the cycle started
    store.refresh(['INFY.NS']);
    vi.advanceTimersByTime(LIVE_QUOTE_TTL_MS - 2_000); // next 5-minute tick
    store.refresh(['INFY.NS']);

    expect(api.getLatestQuote).toHaveBeenCalledTimes(2);
  });

  it('does not refetch when another view asks for the same symbol mid-cycle', () => {
    vi.useFakeTimers();
    store.refresh(['INFY.NS']);
    vi.advanceTimersByTime(60_000);
    store.refresh(['INFY.NS']);
    vi.advanceTimersByTime(3 * 60_000);
    store.refresh(['INFY.NS']);

    expect(api.getLatestQuote).toHaveBeenCalledTimes(1);
  });

  it('shows loading until the response arrives and skips symbols already in flight', () => {
    const pending = new Subject<CachedQuoteResponse>();
    api.getLatestQuote.mockReturnValue(pending);

    store.refresh(['AAPL']);
    store.refresh(['AAPL'], true);
    expect(store.entry('AAPL')?.status).toBe('loading');
    expect(api.getLatestQuote).toHaveBeenCalledTimes(1);

    pending.next(quote('AAPL', 190.1));
    pending.complete();
    expect(store.entry('AAPL')?.status).toBe('ready');
  });

  it('marks a symbol missing when the cache has no quote (MKT-404)', () => {
    api.getLatestQuote.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'MKT-404' } })),
    );

    store.refresh(['ACME']);

    expect(store.entry('ACME')?.status).toBe('missing');
    expect(store.quote('ACME')).toBeNull();
  });

  it('keeps the last good quote when a refresh fails', () => {
    store.refresh(['INFY.NS']);
    api.getLatestQuote.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    store.refresh(['INFY.NS'], true);

    expect(store.entry('INFY.NS')?.status).toBe('error');
    expect(store.quote('INFY.NS')?.price).toBe(1006.35);
  });

  it('track() fetches when the symbol list changes and re-checks every TTL until destroyed', () => {
    vi.useFakeTimers();
    const symbols = signal(['INFY.NS']);

    @Component({ standalone: true, template: '' })
    class HostComponent {
      constructor() {
        TestBed.inject(LiveQuoteStore).track(() => symbols());
      }
    }

    const fixture = TestBed.runInInjectionContext(() => TestBed.createComponent(HostComponent));
    fixture.detectChanges();
    TestBed.tick();
    expect(api.getLatestQuote).toHaveBeenCalledWith('INFY.NS');

    symbols.set(['INFY.NS', 'AAPL']);
    TestBed.tick();
    expect(api.getLatestQuote).toHaveBeenCalledWith('AAPL');
    expect(api.getLatestQuote).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(LIVE_QUOTE_TTL_MS);
    expect(api.getLatestQuote).toHaveBeenCalledTimes(4);

    fixture.destroy();
    vi.advanceTimersByTime(LIVE_QUOTE_TTL_MS * 3);
    expect(api.getLatestQuote).toHaveBeenCalledTimes(4);
  });

  describe('track() with a custom interval', () => {
    const LIVE_QUOTE_FAST_POLL_MS = 15_000;
    let visibility: DocumentVisibilityState;

    const host = (symbols: () => string[]) => {
      @Component({ standalone: true, template: '' })
      class LiveHostComponent {
        constructor() {
          TestBed.inject(LiveQuoteStore).track(symbols, LIVE_QUOTE_FAST_POLL_MS);
        }
      }
      const fixture = TestBed.runInInjectionContext(() => TestBed.createComponent(LiveHostComponent));
      fixture.detectChanges();
      TestBed.tick();
      return fixture;
    };

    beforeEach(() => {
      vi.useFakeTimers();
      visibility = 'visible';
      vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    });

    afterEach(() => vi.restoreAllMocks());

    it('re-reads the quote cache every poll interval', () => {
      const fixture = host(() => ['INFY.NS']);
      expect(api.getLatestQuote).toHaveBeenCalledTimes(1);

      vi.advanceTimersByTime(LIVE_QUOTE_FAST_POLL_MS);
      expect(api.getLatestQuote).toHaveBeenCalledTimes(2);
      vi.advanceTimersByTime(LIVE_QUOTE_FAST_POLL_MS);
      expect(api.getLatestQuote).toHaveBeenCalledTimes(3);

      fixture.destroy();
    });

    it('pauses while the tab is hidden and catches up when it is shown again', () => {
      const fixture = host(() => ['INFY.NS']);
      visibility = 'hidden';
      document.dispatchEvent(new Event('visibilitychange'));

      vi.advanceTimersByTime(LIVE_QUOTE_FAST_POLL_MS * 4);
      expect(api.getLatestQuote).toHaveBeenCalledTimes(1);

      visibility = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
      expect(api.getLatestQuote).toHaveBeenCalledTimes(2);

      fixture.destroy();
    });
  });
});
