import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';
import { WatchlistStore } from './watchlist.store';
import { WatchlistResponse, WatchlistsService } from '../../api/generated/watchlist';
import { CoreAuthService } from '../../core/auth/core-auth.service';

const tech = (): WatchlistResponse => ({
  watchlistId: 1,
  accountId: 7,
  name: 'Tech',
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  items: [{ watchlistItemId: 10, watchlistId: 1, accountId: 7, symbol: 'AAPL', createdAt: '2026-10-01T00:00:00Z' }],
});

describe('WatchlistStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let store: WatchlistStore;

  beforeEach(() => {
    api = {
      getWatchlists: vi.fn(() => of([tech()])),
      createWatchlist: vi.fn((_: number, body: { name: string }) =>
        of({ ...tech(), watchlistId: 2, name: body.name, items: [] }),
      ),
      deleteWatchlist: vi.fn(() => of(null)),
      addWatchlistItem: vi.fn((_: number, watchlistId: number, body: { symbol: string }) =>
        of({ watchlistItemId: 11, watchlistId, accountId: 7, symbol: body.symbol, createdAt: '2026-10-02T00:00:00Z' }),
      ),
      removeWatchlistItem: vi.fn(() => of(null)),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: WatchlistsService, useValue: api },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7 }) } },
      ],
    });
    store = TestBed.inject(WatchlistStore);
  });

  it('loads the signed-in account’s watchlists', () => {
    store.load();

    expect(api['getWatchlists']).toHaveBeenCalledWith(7);
    expect(store.watchlists().map((w) => w.name)).toEqual(['Tech']);
    expect(store.isLoaded()).toBe(true);
    expect(store.totalItems()).toBe(1);
  });

  it('creates, adds, removes and deletes, keeping local state in step', async () => {
    store.load();

    const created = await firstValueFrom(store.create('  Banks '));
    expect(api['createWatchlist']).toHaveBeenCalledWith(7, { name: 'Banks' });
    expect(store.watchlists().map((w) => w.watchlistId)).toEqual([1, 2]);

    await firstValueFrom(store.addSymbol(created.watchlistId, 'JPM'));
    expect(store.find(2)!.items.map((i) => i.symbol)).toEqual(['JPM']);
    expect(store.listsContaining('JPM').map((w) => w.name)).toEqual(['Banks']);

    await firstValueFrom(store.removeSymbol(1, 'AAPL'));
    expect(api['removeWatchlistItem']).toHaveBeenCalledWith(7, 1, 10);
    expect(store.find(1)!.items).toEqual([]);

    await firstValueFrom(store.delete(1));
    expect(store.watchlists().map((w) => w.watchlistId)).toEqual([2]);
  });

  it('surfaces the API error message', async () => {
    api['createWatchlist'].mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { errorCode: 'WL-409', message: "A watchlist named 'Tech' already exists" } })),
    );

    await expect(firstValueFrom(store.create('Tech'))).rejects.toThrow("A watchlist named 'Tech' already exists");
  });

  it('records a load failure', () => {
    api['getWatchlists'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    store.load();

    expect(store.error()).toBe('Cannot reach the trading service.');
    expect(store.watchlists()).toEqual([]);
  });
});
