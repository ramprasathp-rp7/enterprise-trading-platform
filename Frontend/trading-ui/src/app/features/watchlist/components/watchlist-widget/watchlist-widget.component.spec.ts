import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { WatchlistWidgetComponent } from './watchlist-widget.component';
import { WatchlistStore } from '../../watchlist.store';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { WatchlistResponse } from '../../../../api/generated/watchlist';

const list = (watchlistId: number, name: string, symbols: string[]): WatchlistResponse => ({
  watchlistId,
  accountId: 7,
  name,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  items: symbols.map((symbol, i) => ({ watchlistItemId: watchlistId * 100 + i, watchlistId, accountId: 7, symbol, createdAt: '' })),
});

describe('WatchlistWidgetComponent', () => {
  let fixture: ComponentFixture<WatchlistWidgetComponent>;
  let component: WatchlistWidgetComponent;
  const watchlists = signal<WatchlistResponse[]>([]);
  const loading = signal(false);
  const loaded = signal(true);
  const error = signal<string | null>(null);
  const load = vi.fn();
  const entries: Record<string, unknown> = {
    AAPL: { status: 'ready', quote: { price: 336.67, currency: 'USD', changePercent: 0.91, receivedAt: '2026-10-09T10:00:00Z' } },
    MSFT: { status: 'ready', quote: { price: 512.4, currency: 'USD', changePercent: -0.79, receivedAt: '2026-10-09T10:00:00Z' } },
    NVDA: { status: 'missing', quote: null },
    TSLA: { status: 'loading', quote: null },
  };

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    watchlists.set([
      list(1, 'Tech giants', ['AAPL', 'MSFT', 'NVDA', 'TSLA', 'PFE', 'JPM', 'SHOP']),
      list(2, 'India', []),
    ]);
    loading.set(false);
    loaded.set(true);
    error.set(null);
    await TestBed.configureTestingModule({
      imports: [WatchlistWidgetComponent],
      providers: [
        provideRouter([]),
        { provide: WatchlistStore, useValue: { watchlists, isLoading: loading, isLoaded: loaded, error, load } },
        { provide: LiveQuoteStore, useValue: { entry: (s: string) => entries[s] ?? null, track: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WatchlistWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the first list with live prices, changes and how many more there are', () => {
    expect(load).toHaveBeenCalled();
    const rows = component.rows();
    expect(rows.map((r) => r.symbol)).toEqual(['AAPL', 'MSFT', 'NVDA', 'TSLA', 'PFE']);
    expect(rows[0].price).toBe('$336.67');
    expect(rows[1].changePercent).toBe(-0.79);
    expect(rows[2]).toEqual(expect.objectContaining({ price: '—', title: 'No price yet' }));
    expect(rows[3].price).toBe('…');
    expect(rows[0].title).toContain('Updated');
    expect(component.hiddenCount()).toBe(2);
    expect(text()).toContain('+0.91%');
    expect(text()).toContain('-0.79%');
    expect(text()).toContain('+2 more');
  });

  it('switches lists from the tabs and explains an empty one', () => {
    const tabs = fixture.nativeElement.querySelectorAll('.tabs button') as NodeListOf<HTMLButtonElement>;
    tabs[1].click();
    fixture.detectChanges();

    expect(component.current()?.name).toBe('India');
    expect(text()).toContain('is empty');
  });

  it('shows loading, an error, and the no-watchlists state', () => {
    loading.set(true);
    loaded.set(false);
    fixture.detectChanges();
    expect(text()).toContain('Loading');

    // The error only replaces the widget when there is nothing loaded to show
    loading.set(false);
    watchlists.set([]);
    error.set('Service down');
    fixture.detectChanges();
    expect(text()).toContain('Watchlists unavailable');

    error.set(null);
    loaded.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="watchlist-widget-empty"]')).not.toBeNull();
  });
});
