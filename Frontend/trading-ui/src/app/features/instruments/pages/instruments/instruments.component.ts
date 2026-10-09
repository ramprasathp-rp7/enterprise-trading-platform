import { AnimationCallbackEvent, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { AccountSettingsStore } from '../../../../core/settings/account-settings.store';
import { AssetClass, Exchange, Instrument } from '../../instrument.models';
import {
  InstrumentCatalogService,
  MarketGroup,
  exchangeOf,
  formatInstrumentPrice,
  inMarket,
  marketLabel,
} from '../../services/instrument-catalog.service';
import { AddToWatchlistComponent } from '../../../watchlist/components/add-to-watchlist/add-to-watchlist.component';
import { PriceAlertButtonComponent } from '../../../watchlist/alerts/components/price-alert-button/price-alert-button.component';
import { FlashOnChangeDirective } from '../../../../shared/flash-on-change.directive';

export type InstrumentSortKey = 'symbol' | 'name' | 'exchange' | 'price' | 'change';
type ClassFilter = AssetClass | 'ALL';

/** One instrument with the market data the table shows */
export interface InstrumentRow {
  instrument: Instrument;
  exchange: Exchange;
  price: number | null;
  change: number | null;
  changePercent: number | null;
  bid: number | null;
  ask: number | null;
  previousClose: number | null;
  quoteAsOf: string | null;
  receivedAt: string | null;
  marketState: string | null;
  stale: boolean;
  status: 'loading' | 'ready' | 'missing' | 'error';
}

/**
 * Instruments page: every listed instrument with its latest market data, filtered by market,
 * class and text, sortable, with a Buy / Sell shortcut into the order ticket. Symbols the
 * platform does not list yet can be looked up in market data and added (symbol discovery).
 */
@Component({
  selector: 'app-instruments',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AddToWatchlistComponent, PriceAlertButtonComponent, FlashOnChangeDirective],
  templateUrl: './instruments.component.html',
  styleUrls: ['./instruments.component.css'],
})
export class InstrumentsComponent {
  private readonly catalog = inject(InstrumentCatalogService);
  private readonly liveQuotes = inject(LiveQuoteStore);
  private readonly settings = inject(AccountSettingsStore);
  private readonly router = inject(Router);

  readonly query = signal('');
  readonly market = signal<MarketGroup>('ALL');
  /** Set once the user picks a market here, so the preference stops overriding it */
  private readonly marketTouched = signal(false);
  readonly assetClass = signal<ClassFilter>('ALL');
  readonly tradableOnly = signal(false);
  readonly sortKey = signal<InstrumentSortKey>('symbol');
  readonly sortDir = signal<'asc' | 'desc'>('asc');
  readonly expanded = signal<string | null>(null);

  readonly discoverText = signal('');
  readonly discovering = signal(false);
  readonly discoverFeedback = signal<{ text: string; error: boolean } | null>(null);

  readonly marketOptions: { value: MarketGroup; label: string }[] = [
    { value: 'ALL', label: 'All markets' },
    { value: 'USA', label: 'USA' },
    { value: 'NSE', label: 'NSE' },
  ];
  readonly classOptions: { value: ClassFilter; label: string }[] = [
    { value: 'ALL', label: 'All classes' },
    { value: 'EQUITY', label: 'Equity' },
    { value: 'FX', label: 'FX' },
    { value: 'CRYPTO', label: 'Crypto' },
  ];

  readonly preferredMarket = this.settings.market;
  readonly marketName = computed(() => marketLabel(this.market()));

  /** Every listed instrument with its live quote */
  private readonly allRows = computed<InstrumentRow[]>(() =>
    this.catalog.all().map((instrument) => {
      const entry = this.liveQuotes.entry(instrument.symbol);
      const q = entry?.quote ?? null;
      return {
        instrument,
        exchange: exchangeOf(instrument),
        price: q ? Number(q.price) : null,
        change: q?.change ?? null,
        changePercent: q?.changePercent ?? null,
        bid: q?.bid ?? null,
        ask: q?.ask ?? null,
        previousClose: q?.previousClose ?? null,
        quoteAsOf: q?.quoteAsOf ?? null,
        receivedAt: q?.receivedAt ?? null,
        marketState: q?.marketState ?? null,
        stale: !!q?.stale,
        status: entry?.status ?? 'loading',
      };
    }),
  );

  readonly marketCounts = computed<Record<MarketGroup, number>>(() => {
    const all = this.allRows();
    return {
      ALL: all.length,
      USA: all.filter((r) => inMarket(r.instrument, 'USA')).length,
      NSE: all.filter((r) => inMarket(r.instrument, 'NSE')).length,
    };
  });

  /** The rows after filters and sorting */
  readonly rows = computed<InstrumentRow[]>(() => {
    const q = this.query().trim().toUpperCase();
    const market = this.market();
    const assetClass = this.assetClass();
    const tradableOnly = this.tradableOnly();
    const filtered = this.allRows().filter(
      (r) =>
        inMarket(r.instrument, market) &&
        (assetClass === 'ALL' || r.instrument.assetClass === assetClass) &&
        (!tradableOnly || r.instrument.tradable) &&
        (!q || r.instrument.symbol.toUpperCase().includes(q) || r.instrument.name.toUpperCase().includes(q)),
    );
    const key = this.sortKey();
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    const value = (r: InstrumentRow): string | number | null =>
      key === 'symbol'
        ? r.instrument.symbol
        : key === 'name'
          ? r.instrument.name
          : key === 'exchange'
            ? r.exchange
            : key === 'price'
              ? r.price
              : r.changePercent;
    return [...filtered].sort((a, b) => {
      const va = value(a);
      const vb = value(b);
      // Missing numbers always sink to the bottom, whatever the direction
      if (va === null || vb === null) {
        return va === null && vb === null ? 0 : va === null ? 1 : -1;
      }
      const cmp = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb));
      return cmp * dir || a.instrument.symbol.localeCompare(b.instrument.symbol);
    });
  });

  readonly activeFilters = computed(
    () => !!this.query().trim() || this.assetClass() !== 'ALL' || this.tradableOnly() || this.market() !== 'ALL',
  );

  constructor() {
    // Start from the market preference until the user picks one here
    effect(() => {
      const preferred = this.settings.market();
      if (!untracked(() => this.marketTouched())) {
        this.market.set(preferred);
      }
    });
    // Keep the quotes of the listed instruments fresh (one request per symbol per refresh)
    this.liveQuotes.track(() => this.rows().map((r) => r.instrument.symbol));
  }

  setMarket(market: MarketGroup): void {
    this.market.set(market);
    this.marketTouched.set(true);
  }

  sortBy(key: InstrumentSortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      // Prices and moves read best biggest first
      this.sortDir.set(key === 'price' || key === 'change' ? 'desc' : 'asc');
    }
  }

  sortIcon(key: InstrumentSortKey): string {
    if (this.sortKey() !== key) {
      return 'fa-sort';
    }
    return this.sortDir() === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  clearFilters(): void {
    this.query.set('');
    this.assetClass.set('ALL');
    this.tradableOnly.set(false);
    this.setMarket('ALL');
  }

  toggle(symbol: string): void {
    this.expanded.set(this.expanded() === symbol ? null : symbol);
  }

  /**
   * Slides an instrument's details open or closed. Angular waits for animationComplete() before
   * removing a closing row. The content is clipped only while it slides, so the watchlist and
   * alert menus inside are never cut off.
   */
  slideDetails(event: AnimationCallbackEvent, direction: 'open' | 'close'): void {
    const clip = (event.target as HTMLElement).querySelector<HTMLElement>('.details-clip');
    const inner = clip?.querySelector<HTMLElement>('.details-inner');
    const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!clip || !inner || reduced || typeof clip.animate !== 'function') {
      event.animationComplete();
      return;
    }
    const shut = { gridTemplateRows: '0fr', opacity: 0 };
    const full = { gridTemplateRows: '1fr', opacity: 1 };
    inner.style.overflow = 'hidden';
    const animation = clip.animate(direction === 'open' ? [shut, full] : [full, shut], {
      duration: direction === 'open' ? 260 : 200,
      easing: direction === 'open' ? 'cubic-bezier(0.2, 0, 0, 1)' : 'cubic-bezier(0.4, 0, 1, 1)',
    });
    const done = () => {
      inner.style.overflow = '';
      event.animationComplete();
    };
    animation.finished.then(done, done);
  }

  trade(row: InstrumentRow, side: 'BUY' | 'SELL', event?: Event): void {
    event?.stopPropagation();
    this.router.navigate(['/order-ticket'], { queryParams: { symbol: row.instrument.symbol, side } });
  }

  price(value: number | null, symbol: string): string {
    return formatInstrumentPrice(value, symbol);
  }

  spread(row: InstrumentRow): string {
    if (row.bid === null || row.ask === null) {
      return '—';
    }
    return formatInstrumentPrice(Number(row.ask) - Number(row.bid), row.instrument.symbol);
  }

  /** Symbol discovery: list a symbol market data knows */
  discover(): void {
    const symbol = this.discoverText().trim().toUpperCase();
    if (!symbol || this.discovering()) {
      return;
    }
    this.discovering.set(true);
    this.discoverFeedback.set(null);
    this.catalog.discover(symbol).subscribe({
      next: ({ instrument, created }) => {
        this.discovering.set(false);
        this.discoverText.set('');
        this.discoverFeedback.set({
          text: created
            ? `Added ${instrument.symbol} (${instrument.name}). It now has live prices and can be traded${instrument.tradable ? '' : ' once it is active'}.`
            : `${instrument.symbol} is already listed.`,
          error: false,
        });
        // Show it: make sure the filters let it through, then open its details
        if (!inMarket(instrument, this.market())) {
          this.setMarket('ALL');
        }
        this.query.set(instrument.symbol);
        this.expanded.set(instrument.symbol);
      },
      error: (err: unknown) => {
        this.discovering.set(false);
        const http = err instanceof HttpErrorResponse ? err : null;
        this.discoverFeedback.set({
          text:
            (http?.error as { message?: string } | null)?.message ??
            (http?.status === 0 ? 'Cannot reach the trading service.' : `No market data was found for ${symbol}.`),
          error: true,
        });
      },
    });
  }
}
