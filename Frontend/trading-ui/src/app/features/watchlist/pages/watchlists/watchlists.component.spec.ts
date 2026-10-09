import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WatchlistRow, WatchlistsComponent, sortRows } from './watchlists.component';
import { Router } from '@angular/router';
import { WatchlistResponse, WatchlistsService } from '../../../../api/generated/watchlist';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { MarketDataService } from '../../../../api/generated/market-data';

describe('WatchlistsComponent', () => {
  let fixture: ComponentFixture<WatchlistsComponent>;
  let component: WatchlistsComponent;
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let marketData: { getLatestQuote: ReturnType<typeof vi.fn> };

  const lists = (): WatchlistResponse[] => [
    {
      watchlistId: 1,
      accountId: 7,
      name: 'Tech',
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-01T00:00:00Z',
      items: [
        { watchlistItemId: 10, watchlistId: 1, accountId: 7, symbol: 'AAPL', createdAt: '2026-10-01T00:00:00Z' },
        { watchlistItemId: 11, watchlistId: 1, accountId: 7, symbol: 'TATASTEEL.BO', createdAt: '2026-10-01T00:00:00Z' },
      ],
    },
    { watchlistId: 2, accountId: 7, name: 'Crypto', createdAt: '2026-10-02T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z', items: [] },
  ];

  beforeEach(async () => {
    api = {
      getWatchlists: vi.fn(() => of(lists())),
      createWatchlist: vi.fn(),
      deleteWatchlist: vi.fn(() => of(null)),
      addWatchlistItem: vi.fn((_: number, watchlistId: number, body: { symbol: string }) =>
        of({ watchlistItemId: 12, watchlistId, accountId: 7, symbol: body.symbol, createdAt: '2026-10-03T00:00:00Z' }),
      ),
      removeWatchlistItem: vi.fn(() => of(null)),
    };
    marketData = {
      getLatestQuote: vi.fn((symbol: string) =>
        symbol === 'AAPL'
          ? of({ symbol: 'AAPL', price: 190.1, bid: 190, ask: 190.2, currency: 'USD', changePercent: 1.25, stale: false, receivedAt: '2026-10-06T09:05:00Z' })
          : throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'MKT-404' } })),
      ),
    };
    await TestBed.configureTestingModule({
      imports: [WatchlistsComponent],
      providers: [
        provideRouter([]),
        { provide: WatchlistsService, useValue: api },
        { provide: MarketDataService, useValue: marketData },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7 }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WatchlistsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists watchlists and shows the first one’s instruments with live prices', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.list-entry').length).toBe(2);
    expect(component.selected()?.name).toBe('Tech');
    expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL', 'TATASTEEL.BO']);
    // live price from the backend quote cache; a symbol the cache lacks shows a dash
    expect(marketData.getLatestQuote).toHaveBeenCalledWith('AAPL');
    expect(component.rows()[0].price).toBe(190.1);
    expect(component.rows()[0].changePercent).toBe(1.25);
    expect(component.rows()[1].price).toBeNull();
    expect(component.rows()[1].priceStatus).toBe('missing');
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="price-AAPL"]')!.textContent).toContain('190.10');
    expect(el.querySelector('[data-testid="price-TATASTEEL.BO"]')!.textContent).toContain('—');
    // untradable instruments get no Trade button
    expect(el.querySelector('[data-testid="watch-row-AAPL"] a')!.textContent).toContain('Trade');
    expect(el.querySelector('[data-testid="watch-row-TATASTEEL.BO"]')!.textContent).toContain('Not tradable');
  });

  it('adds an instrument picked from search to the selected watchlist', () => {
    component.addInstrument({ id: 8, symbol: 'MSFT', name: 'Microsoft Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true });

    expect(api['addWatchlistItem']).toHaveBeenCalledWith(7, 1, { symbol: 'MSFT' });
    expect(component.rows().map((r) => r.item.symbol)).toContain('MSFT');
  });

  it('does not call the API for an instrument already in the list', () => {
    component.addInstrument({ id: 2, symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true });

    expect(api['addWatchlistItem']).not.toHaveBeenCalled();
    expect(component.feedback()?.error).toBe(true);
  });

  it('removes an instrument', () => {
    component.removeItem(component.rows()[0]);

    expect(api['removeWatchlistItem']).toHaveBeenCalledWith(7, 1, 10);
    expect(component.rows().map((r) => r.item.symbol)).toEqual(['TATASTEEL.BO']);
  });

  it('asks for confirmation before deleting a watchlist', () => {
    component.deleteWatchlist(2);
    expect(api['deleteWatchlist']).not.toHaveBeenCalled();

    component.deleteWatchlist(2);
    expect(api['deleteWatchlist']).toHaveBeenCalledWith(7, 2);
    expect(component.store.watchlists().map((w) => w.name)).toEqual(['Tech']);
  });

  describe('sorting and filtering', () => {
    beforeEach(() => localStorage.removeItem('etp-watchlist-sort'));

    it('keeps the watchlist order by default and sorts on request, remembering the choice', () => {
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL', 'TATASTEEL.BO']);

      component.setSort('symbol');
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL', 'TATASTEEL.BO']);
      component.setSort('added-desc');
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['TATASTEEL.BO', 'AAPL']);
      expect(localStorage.getItem('etp-watchlist-sort')).toBe('added-desc');
    });

    it('puts instruments without a price last whatever the direction', () => {
      component.setSort('price-asc');
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL', 'TATASTEEL.BO']);
      component.setSort('price-desc');
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL', 'TATASTEEL.BO']);
    });

    it('filters by text, class and move, and can clear the filters', () => {
      component.filterText.set('tata');
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['TATASTEEL.BO']);

      component.filterText.set('');
      component.moveFilter.set('gainers');
      expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL']);
      component.moveFilter.set('losers');
      expect(component.rows()).toEqual([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="watch-no-match"]')).not.toBeNull();

      component.clearFilters();
      component.classFilter.set('CRYPTO');
      expect(component.rows()).toEqual([]);
      component.classFilter.set('EQUITY');
      expect(component.rows().length).toBe(2);
    });
  });

  describe('sortRows', () => {
    const row = (symbol: string, name: string, price: number | null, change: number | null): WatchlistRow => ({
      item: { watchlistItemId: symbol.length, watchlistId: 1, accountId: 7, symbol, createdAt: '' },
      name, assetClass: 'EQUITY', currency: 'USD', currencySymbol: '$', tradable: true,
      price, changePercent: change, stale: false, receivedAt: null, priceStatus: 'ready', decimals: 2,
    } as WatchlistRow);
    const rows = [row('MSFT', 'Microsoft', 500, -1), row('AAPL', 'Apple', 190, 2), row('ZZZ', 'Zed', null, null), row('NVDA', 'Nvidia', 190, 2)];
    const symbols = (sorted: WatchlistRow[]) => sorted.map((r) => r.item.symbol);

    it('sorts by name, change and price with ties broken by symbol and blanks last', () => {
      expect(symbols(sortRows(rows, 'name'))).toEqual(['AAPL', 'MSFT', 'NVDA', 'ZZZ']);
      expect(symbols(sortRows(rows, 'change-desc'))).toEqual(['AAPL', 'NVDA', 'MSFT', 'ZZZ']);
      expect(symbols(sortRows(rows, 'change-asc'))).toEqual(['MSFT', 'AAPL', 'NVDA', 'ZZZ']);
      expect(symbols(sortRows(rows, 'price-asc'))).toEqual(['AAPL', 'NVDA', 'MSFT', 'ZZZ']);
      expect(symbols(sortRows([row('A', 'a', null, null), row('B', 'b', null, null)], 'price-desc'))).toEqual(['A', 'B']);
    });

  });

  describe('managing lists', () => {
    it('creates a list and opens it', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      api['createWatchlist'].mockReturnValue(of({ watchlistId: 3, accountId: 7, name: 'Growth', createdAt: '', updatedAt: '', items: [] }));
      component.newName = '  Growth ';

      component.createWatchlist();

      expect(api['createWatchlist']).toHaveBeenCalledWith(7, { name: 'Growth' });
      expect(component.feedback()?.text).toContain('Growth');
      expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { id: 3 } }));
      expect(component.newName).toBe('');
    });

    it('ignores a blank name and shows a failed create', () => {
      component.newName = '  ';
      component.createWatchlist();
      expect(api['createWatchlist']).not.toHaveBeenCalled();

      api['createWatchlist'].mockReturnValue(throwError(() => new Error('Name taken')));
      component.newName = 'Tech';
      component.createWatchlist();
      expect(component.feedback()?.error).toBe(true);
      expect(component.creating()).toBe(false);
    });

    it('deletes the open list after confirmation and forgets it in the URL', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      (component as unknown as { requestedId: { set(v: number): void } }).requestedId.set(1);

      component.deleteWatchlist(1);
      component.deleteWatchlist(1);

      expect(api['deleteWatchlist']).toHaveBeenCalledWith(7, 1);
      expect(component.feedback()?.text).toContain('Deleted');
      expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { id: null } }));
    });

    it('shows a failed delete, add and remove', () => {
      api['deleteWatchlist'].mockReturnValue(throwError(() => new Error('Delete failed')));
      component.deleteWatchlist(2);
      component.deleteWatchlist(2);
      expect(component.feedback()?.error).toBe(true);
      expect(component.confirmDeleteId()).toBeNull();

      api['addWatchlistItem'].mockReturnValue(throwError(() => new Error('Add failed')));
      component.addInstrument({ id: 8, symbol: 'MSFT', name: 'Microsoft', assetClass: 'EQUITY', currency: 'USD', tradable: true });
      expect(component.feedback()?.error).toBe(true);

      api['removeWatchlistItem'].mockReturnValue(throwError(() => new Error('Remove failed')));
      component.removeItem(component.rows()[0]);
      expect(component.feedback()?.error).toBe(true);
      expect(component.removingIds().size).toBe(0);
    });

    it('explains each price state on hover', () => {
      const base = component.rows()[0];
      expect(component.priceTitle({ ...base, priceStatus: 'loading' })).toContain('Loading');
      expect(component.priceTitle({ ...base, priceStatus: 'missing' })).toContain('No price yet');
      expect(component.priceTitle({ ...base, priceStatus: 'ready', receivedAt: null })).toBe('Price unavailable right now.');
      expect(component.priceTitle({ ...base, priceStatus: 'error', stale: true })).toContain('delayed quote');
      expect(component.priceTitle({ ...base, priceStatus: 'error' })).toContain('could not refresh');
      expect(component.formatPrice({ ...base, price: null })).toBe('—');
    });

    it('knows when any filter is applied', () => {
      expect(component.filtersActive()).toBe(false);
      component.classFilter.set('FX');
      expect(component.filtersActive()).toBe(true);
    });
  });
});
