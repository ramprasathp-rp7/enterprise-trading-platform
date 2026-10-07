import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WatchlistsComponent } from './watchlists.component';
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
});
