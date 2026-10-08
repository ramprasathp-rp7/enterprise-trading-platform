import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, map, tap, catchError, throwError } from 'rxjs';
import {
  WatchlistItemResponse,
  WatchlistResponse,
  WatchlistsService,
} from '../../api/generated/watchlist';
import { CoreAuthService } from '../../core/auth/core-auth.service';

/**
 * Watchlist state shared by the Watchlists page, the dashboard widget and the
 * "Add to watchlist" menu on instrument details. All calls go through the
 * generated Watchlist API client (contracts/watchlist-api.yaml).
 */
@Injectable({ providedIn: 'root' })
export class WatchlistStore {
  private readonly api = inject(WatchlistsService);
  private readonly authService = inject(CoreAuthService);

  private readonly listsSignal = signal<WatchlistResponse[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly loadedSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly watchlists = this.listsSignal.asReadonly();
  readonly isLoading = this.loadingSignal.asReadonly();
  /** True once the first load has completed (successfully or not) */
  readonly isLoaded = this.loadedSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly totalItems = computed(() => this.listsSignal().reduce((sum, w) => sum + w.items.length, 0));

  constructor() {
    // Never show one user's lists to the next user who signs in on this tab
    let previous: number | null | undefined;
    effect(() => {
      const accountId = this.authService.currentUser()?.accountId ?? null;
      if (previous !== undefined && previous !== accountId) {
        untracked(() => this.reset());
      }
      previous = accountId;
    });
  }

  private get accountId(): number | null {
    return this.authService.currentUser()?.accountId ?? null;
  }

  /** Fetch every watchlist of the signed-in account */
  load(): void {
    const accountId = this.accountId;
    if (!accountId) {
      return;
    }
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    this.api.getWatchlists(accountId).subscribe({
      next: (lists) => {
        this.listsSignal.set(lists ?? []);
        this.loadingSignal.set(false);
        this.loadedSignal.set(true);
      },
      error: (err) => {
        this.errorSignal.set(messageOf(err, 'Could not load your watchlists.'));
        this.loadingSignal.set(false);
        this.loadedSignal.set(true);
      },
    });
  }

  /** Load once per session; later visits reuse the cached lists */
  ensureLoaded(): void {
    if (!this.loadedSignal() && !this.loadingSignal()) {
      this.load();
    }
  }

  find(watchlistId: number | null | undefined): WatchlistResponse | null {
    return this.listsSignal().find((w) => w.watchlistId === watchlistId) ?? null;
  }

  /** Watchlists that already contain a symbol */
  listsContaining(symbol: string): WatchlistResponse[] {
    return this.listsSignal().filter((w) => w.items.some((i) => i.symbol === symbol));
  }

  create(name: string): Observable<WatchlistResponse> {
    return this.call((accountId) => this.api.createWatchlist(accountId, { name: name.trim() })).pipe(
      tap((created) => this.listsSignal.update((lists) => [...lists, created])),
    );
  }

  delete(watchlistId: number): Observable<void> {
    return this.call((accountId) => this.api.deleteWatchlist(accountId, watchlistId)).pipe(
      tap(() => this.listsSignal.update((lists) => lists.filter((w) => w.watchlistId !== watchlistId))),
      map(() => undefined),
    );
  }

  addSymbol(watchlistId: number, symbol: string): Observable<WatchlistItemResponse> {
    return this.call((accountId) => this.api.addWatchlistItem(accountId, watchlistId, { symbol })).pipe(
      tap((item) =>
        this.patch(watchlistId, (w) => ({ ...w, updatedAt: item.createdAt, items: [...w.items, item] })),
      ),
    );
  }

  removeItem(watchlistId: number, watchlistItemId: number): Observable<void> {
    return this.call((accountId) => this.api.removeWatchlistItem(accountId, watchlistId, watchlistItemId)).pipe(
      tap(() =>
        this.patch(watchlistId, (w) => ({
          ...w,
          updatedAt: new Date().toISOString(),
          items: w.items.filter((i) => i.watchlistItemId !== watchlistItemId),
        })),
      ),
      map(() => undefined),
    );
  }

  /** Remove a symbol from a watchlist without knowing its item id */
  removeSymbol(watchlistId: number, symbol: string): Observable<void> {
    const item = this.find(watchlistId)?.items.find((i) => i.symbol === symbol);
    if (!item) {
      return throwError(() => new Error(`${symbol} is not in this watchlist.`));
    }
    return this.removeItem(watchlistId, item.watchlistItemId);
  }

  /** Clears cached state (e.g. on sign-out) */
  reset(): void {
    this.listsSignal.set([]);
    this.loadedSignal.set(false);
    this.errorSignal.set(null);
  }

  private patch(watchlistId: number, change: (w: WatchlistResponse) => WatchlistResponse): void {
    this.listsSignal.update((lists) => lists.map((w) => (w.watchlistId === watchlistId ? change(w) : w)));
  }

  /** Runs a call for the signed-in account and turns HTTP failures into readable Error messages */
  private call<T>(request: (accountId: number) => Observable<T>): Observable<T> {
    const accountId = this.accountId;
    if (!accountId) {
      return throwError(() => new Error('You need to be signed in to manage watchlists.'));
    }
    return request(accountId).pipe(
      catchError((err) => throwError(() => new Error(messageOf(err, 'Something went wrong. Please try again.')))),
    );
  }
}

function messageOf(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    if (body?.message) {
      return body.message;
    }
    if (err.status === 0) {
      return 'Cannot reach the trading service.';
    }
  }
  return fallback;
}
