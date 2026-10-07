import { HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, filter, from, fromEvent, interval, map, merge, mergeMap, of } from 'rxjs';
import { CachedQuoteResponse, MarketDataService } from '../../api/generated/market-data';

/**
 * How often prices are fetched from the backend, and how long a fetched quote is reused:
 * every five minutes, matching the backend's Fauxnance refresh of its quote cache.
 */
export const LIVE_QUOTE_TTL_MS = 5 * 60_000;
/**
 * A quote this close to the TTL counts as due. A refresh tick lands a little under one TTL
 * after the previous response arrived; without this it would skip and refetch a cycle late.
 */
const DUE_TOLERANCE_MS = 10_000;
/** Requests sent at once when many symbols need a quote */
const MAX_CONCURRENT_REQUESTS = 4;

/**
 * What the UI knows about a symbol's live quote:
 * - loading: first request in flight
 * - ready: `quote` holds the newest cached quote
 * - missing: the backend cache has no quote for it yet (MKT-404)
 * - error: the request failed; `quote` keeps the last good value, if any
 */
export interface LiveQuoteEntry {
  status: 'loading' | 'ready' | 'missing' | 'error';
  quote: CachedQuoteResponse | null;
  fetchedAt: number;
}

/**
 * Latest prices from the order service's quote cache, via the generated Market Data API client
 * (contracts/market-data-api.yaml). The backend fills that cache from Fauxnance (or the
 * market-data topic); reading it costs no Fauxnance quota.
 *
 * Shared by every view that shows live prices, so a symbol is fetched once per TTL no matter
 * how many widgets display it.
 */
@Injectable({ providedIn: 'root' })
export class LiveQuoteStore {
  private readonly api = inject(MarketDataService);

  private readonly entries = signal<ReadonlyMap<string, LiveQuoteEntry>>(new Map());
  private readonly inFlight = new Set<string>();

  /** Current entry for a symbol (undefined until first requested) */
  entry(symbol: string): LiveQuoteEntry | undefined {
    return this.entries().get(key(symbol));
  }

  /** Newest cached quote for a symbol, or null if none is known */
  quote(symbol: string): CachedQuoteResponse | null {
    return this.entry(symbol)?.quote ?? null;
  }

  /**
   * Fetches quotes for symbols not fetched within the TTL (all of them with `force`).
   * Symbols already being fetched are skipped.
   */
  refresh(symbols: readonly string[], force = false): void {
    this.refreshOlderThan(symbols, force ? 0 : LIVE_QUOTE_TTL_MS - DUE_TOLERANCE_MS);
  }

  /** Fetches quotes for symbols last fetched at least `maxAgeMs` ago (or never) */
  private refreshOlderThan(symbols: readonly string[], maxAgeMs: number): void {
    const now = Date.now();
    const due = [...new Set(symbols.map(key))].filter((symbol) => {
      if (!symbol || this.inFlight.has(symbol)) {
        return false;
      }
      const entry = this.entries().get(symbol);
      return maxAgeMs <= 0 || !entry || now - entry.fetchedAt >= maxAgeMs;
    });
    if (!due.length) {
      return;
    }

    due.forEach((symbol) => {
      this.inFlight.add(symbol);
      if (!this.entries().has(symbol)) {
        this.set(symbol, { status: 'loading', quote: null, fetchedAt: 0 });
      }
    });

    from(due)
      .pipe(
        mergeMap(
          (symbol) =>
            this.api.getLatestQuote(symbol).pipe(
              map((quote): [string, LiveQuoteEntry] => [symbol, { status: 'ready', quote, fetchedAt: Date.now() }]),
              catchError((err: unknown) => of(this.failed(symbol, err))),
            ),
          MAX_CONCURRENT_REQUESTS,
        ),
      )
      .subscribe(([symbol, entry]) => {
        this.inFlight.delete(symbol);
        this.set(symbol, entry);
      });
  }

  /**
   * Keeps the given symbols' quotes fresh for the lifetime of the calling component:
   * fetches whenever the symbol list changes and again every `everyMs` (five minutes by default). Ticks are skipped while the browser tab is hidden,
   * and quotes are caught up as soon as it is shown again.
   * Views showing the same symbol share one request per cycle.
   * Must be called in an injection context (e.g. a field initialiser or constructor).
   */
  track(symbols: () => readonly string[], everyMs = LIVE_QUOTE_TTL_MS): void {
    const destroyRef = inject(DestroyRef);
    const document = inject(DOCUMENT);
    const maxAgeMs = everyMs - DUE_TOLERANCE_MS;
    effect(() => {
      const list = symbols();
      untracked(() => this.refresh(list));
    });
    merge(interval(everyMs), fromEvent(document, 'visibilitychange'))
      .pipe(
        filter(() => document.visibilityState !== 'hidden'),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.refreshOlderThan(untracked(symbols), maxAgeMs));
  }

  private failed(symbol: string, err: unknown): [string, LiveQuoteEntry] {
    const notCached = err instanceof HttpErrorResponse && err.status === 404;
    return [
      symbol,
      notCached
        ? { status: 'missing', quote: null, fetchedAt: Date.now() }
        : { status: 'error', quote: this.entries().get(symbol)?.quote ?? null, fetchedAt: Date.now() },
    ];
  }

  private set(symbol: string, entry: LiveQuoteEntry): void {
    this.entries.update((entries) => new Map(entries).set(symbol, entry));
  }
}

/** Symbols are cached upper case on the backend */
function key(symbol: string): string {
  return symbol.trim().toUpperCase();
}
