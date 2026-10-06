import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { WatchlistsComponent } from './watchlists.component';
import { WatchlistResponse, WatchlistsService } from '../../../../api/generated/watchlist';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';

describe('WatchlistsComponent', () => {
  let fixture: ComponentFixture<WatchlistsComponent>;
  let component: WatchlistsComponent;
  let api: Record<string, ReturnType<typeof vi.fn>>;

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
    await TestBed.configureTestingModule({
      imports: [WatchlistsComponent],
      providers: [
        provideRouter([]),
        { provide: WatchlistsService, useValue: api },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7 }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WatchlistsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists watchlists and shows the first one’s instruments with sample prices', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.list-entry').length).toBe(2);
    expect(component.selected()?.name).toBe('Tech');
    expect(component.rows().map((r) => r.item.symbol)).toEqual(['AAPL', 'TATASTEEL.BO']);
    expect(component.rows()[0].price).toBeGreaterThan(0);
    // untradable instruments get no Trade button
    expect(el.querySelector('[data-testid="watch-row-AAPL"] a')!.textContent).toContain('Trade');
    expect(el.querySelector('[data-testid="watch-row-TATASTEEL.BO"]')!.textContent).toContain('Not tradable');
  });

  it('adds an instrument picked from search to the selected watchlist', () => {
    component.addInstrument({ symbol: 'MSFT', name: 'Microsoft Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true });

    expect(api['addWatchlistItem']).toHaveBeenCalledWith(7, 1, { symbol: 'MSFT' });
    expect(component.rows().map((r) => r.item.symbol)).toContain('MSFT');
  });

  it('does not call the API for an instrument already in the list', () => {
    component.addInstrument({ symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true });

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
