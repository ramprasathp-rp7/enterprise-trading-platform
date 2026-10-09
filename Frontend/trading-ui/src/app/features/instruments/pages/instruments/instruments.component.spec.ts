import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { InstrumentsComponent } from './instruments.component';
import { MarketDataService } from '../../../../api/generated/market-data';
import { AccountSettingsStore, Market } from '../../../../core/settings/account-settings.store';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { WatchlistsService } from '../../../../api/generated/watchlist';

describe('InstrumentsComponent', () => {
  let fixture: ComponentFixture<InstrumentsComponent>;
  let component: InstrumentsComponent;
  let market: ReturnType<typeof signal<Market>>;
  let discover: ReturnType<typeof vi.fn>;

  const el = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const shown = () => component.rows().map((r) => r.instrument.symbol);

  beforeEach(async () => {
    market = signal<Market>('ALL');
    const prices: Record<string, number> = { AAPL: 336.67, MSFT: 512.4, NVDA: 188.2, 'INFY.NS': 1602.5 };
    await TestBed.configureTestingModule({
      imports: [InstrumentsComponent],
      providers: [
        provideRouter([]),
        { provide: AccountSettingsStore, useValue: { market } },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ id: 'u-7', accountId: 7 }), isAdmin: () => false } },
        { provide: WatchlistsService, useValue: { getWatchlists: vi.fn(() => of([])) } },
        {
          provide: MarketDataService,
          useValue: {
            getLatestQuote: vi.fn((symbol: string) =>
              prices[symbol]
                ? of({ symbol, price: prices[symbol], bid: prices[symbol] - 0.1, ask: prices[symbol] + 0.1, currency: 'USD',
                    change: 1, changePercent: symbol === 'MSFT' ? -0.79 : 0.9, stale: true, receivedAt: '2026-10-09T08:00:00Z' })
                : throwError(() => new HttpErrorResponse({ status: 404 })),
            ),
          },
        },
      ],
    }).compileComponents();
    discover = vi.spyOn(TestBed.inject(InstrumentCatalogService), 'discover') as unknown as ReturnType<typeof vi.fn>;
    fixture = TestBed.createComponent(InstrumentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists every instrument with its live price', () => {
    expect(shown().length).toBe(25);
    expect(el('instrument-price-AAPL')?.textContent).toContain('336.67');
  });

  it('starts from the market preference, and the user can switch market', () => {
    market.set('NSE');
    fixture.detectChanges();
    expect(shown().every((s) => s.endsWith('.NS'))).toBe(true);

    (el('market-filter-USA') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(shown()).toContain('AAPL');
    expect(shown()).not.toContain('INFY.NS');

    // The preference no longer overrides a choice made here
    market.set('NSE');
    fixture.detectChanges();
    expect(shown()).toContain('AAPL');
  });

  it('filters by class, text and tradability', () => {
    (el('class-filter-CRYPTO') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(shown().every((s) => s.startsWith('X:'))).toBe(true);

    (el('class-filter-ALL') as HTMLButtonElement).click();
    component.query.set('tata');
    fixture.detectChanges();
    expect(shown()).toEqual(['TATASTEEL.BO', 'TCS.NS']);

    (el('tradable-only') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(shown()).toEqual(['TCS.NS']);
  });

  it('sorts by price, unpriced instruments last', () => {
    component.sortBy('price');
    fixture.detectChanges();

    expect(shown().slice(0, 4)).toEqual(['INFY.NS', 'MSFT', 'AAPL', 'NVDA']);
    component.sortBy('price');
    expect(shown().slice(0, 4)).toEqual(['NVDA', 'AAPL', 'MSFT', 'INFY.NS']);
    expect(component.rows()[shown().length - 1].price).toBeNull();
  });

  it('opens the order ticket from Buy and Sell', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el('buy-AAPL') as HTMLButtonElement).click();
    (el('sell-MSFT') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/order-ticket'], { queryParams: { symbol: 'AAPL', side: 'BUY' } });
    expect(navigate).toHaveBeenCalledWith(['/order-ticket'], { queryParams: { symbol: 'MSFT', side: 'SELL' } });
    expect((el('buy-TATASTEEL.BO') as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows details for a row', () => {
    (el('instrument-row-AAPL') as HTMLElement).click();
    fixture.detectChanges();

    const details = el('instrument-details-AAPL');
    expect(details?.textContent).toContain('NASDAQ');
    expect(details?.textContent).toContain('Spread');
  });

  it('adds a symbol through discovery and shows it', () => {
    discover.mockReturnValue(
      of({ instrument: { id: 26, symbol: 'SHOP', name: 'Shopify Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true, exchange: 'NYSE' }, created: true }),
    );
    component.discoverText.set('shop');

    component.discover();
    fixture.detectChanges();

    expect(discover).toHaveBeenCalledWith('SHOP');
    expect(el('discover-feedback')?.textContent).toContain('Added SHOP');
    expect(component.query()).toBe('SHOP');
    expect(component.expanded()).toBe('SHOP');
  });

  it('explains a failed discovery', () => {
    discover.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503', message: 'Market data is unavailable right now.' } })),
    );
    component.discoverText.set('shop');

    component.discover();
    fixture.detectChanges();

    expect(el('discover-feedback')?.textContent).toContain('Market data is unavailable right now.');
  });

  describe('details accordion and helpers', () => {
    const detailsRow = () => {
      const row = document.createElement('tr');
      row.innerHTML = '<td><div class="details-clip"><div class="details-inner"></div></div></td>';
      return row;
    };
    const event = (target: HTMLElement) => ({ target, animationComplete: vi.fn() });

    it('slides the details open and closed, clipping only while they move', async () => {
      const row = detailsRow();
      const clip = row.querySelector('.details-clip') as HTMLElement;
      const inner = row.querySelector('.details-inner') as HTMLElement;
      let finish!: () => void;
      clip.animate = vi.fn(() => ({ finished: new Promise<void>((resolve) => (finish = resolve)) })) as never;
      const opening = event(row);

      component.slideDetails(opening as never, 'open');

      expect(inner.style.overflow).toBe('hidden');
      expect((clip.animate as ReturnType<typeof vi.fn>).mock.calls[0][0][0]).toEqual({ gridTemplateRows: '0fr', opacity: 0 });
      finish();
      await Promise.resolve();
      expect(opening.animationComplete).toHaveBeenCalled();
      expect(inner.style.overflow).toBe('');

      const closing = event(row);
      component.slideDetails(closing as never, 'close');
      expect((clip.animate as ReturnType<typeof vi.fn>).mock.calls[1][1]).toEqual(expect.objectContaining({ duration: 200 }));
    });

    it('finishes straight away without the animation API or the row parts', () => {
      const bare = event(document.createElement('tr'));
      component.slideDetails(bare as never, 'open');
      expect(bare.animationComplete).toHaveBeenCalled();

      const row = detailsRow();
      (row.querySelector('.details-clip') as HTMLElement).animate = undefined as never;
      const noApi = event(row);
      component.slideDetails(noApi as never, 'close');
      expect(noApi.animationComplete).toHaveBeenCalled();
    });

    it('clears every filter back to all markets', () => {
      component.query.set('aapl');
      component.assetClass.set('FX');
      component.tradableOnly.set(true);

      component.clearFilters();

      expect(component.query()).toBe('');
      expect(component.assetClass()).toBe('ALL');
      expect(component.tradableOnly()).toBe(false);
      expect(component.market()).toBe('ALL');
    });

    it('shows a dash for a spread without bid and ask, and ignores an empty discovery', () => {
      const row = component.rows()[0];
      expect(component.spread({ ...row, bid: null })).toBe('—');
      expect(component.spread({ ...row, bid: 1, ask: 1.5 })).toContain('0.5');

      component.discoverText.set('   ');
      component.discover();
      expect(component.discovering()).toBe(false);
    });
  });
});
