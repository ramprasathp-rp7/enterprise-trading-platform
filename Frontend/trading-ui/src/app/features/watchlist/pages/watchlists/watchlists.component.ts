import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { WatchlistItemResponse, WatchlistResponse } from '../../../../api/generated/watchlist';
import { InstrumentSearchComponent } from '../../../instruments/components/instrument-search/instrument-search.component';
import { Instrument } from '../../../instruments/instrument.models';
import { InstrumentCatalogService } from '../../../instruments/services/instrument-catalog.service';
import { LiveQuoteEntry, LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { currencySymbol } from '../../../../shared/currency';
import { WatchlistStore } from '../../watchlist.store';
import { PriceAlertButtonComponent } from '../../alerts/components/price-alert-button/price-alert-button.component';
import { PriceAlertsPanelComponent } from '../../alerts/components/price-alerts-panel/price-alerts-panel.component';

/** A watchlist row: the stored item plus catalogue details and its live quote */
export interface WatchlistRow {
  item: WatchlistItemResponse;
  name: string;
  assetClass: string;
  currency: string;
  /** Short symbol for the currency (₹, $, ¥), '' when it has none */
  currencySymbol: string;
  tradable: boolean;
  /** Latest cached price, or null while loading / when the backend has none */
  price: number | null;
  changePercent: number | null;
  /** The quote was past its freshness window when the backend received it */
  stale: boolean;
  /** When the backend received the quote (ISO-8601) */
  receivedAt: string | null;
  priceStatus: LiveQuoteEntry['status'];
  decimals: number;
}

/**
 * Watchlists page: create and delete watchlists, add instruments via search,
 * remove instruments, and jump to the order ticket for any of them.
 * Prices are the latest quotes from the order service's cache (LiveQuoteStore), refreshed every 5 minutes.
 */
@Component({
  selector: 'app-watchlists',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, InstrumentSearchComponent, PriceAlertButtonComponent, PriceAlertsPanelComponent],
  templateUrl: './watchlists.component.html',
  styleUrls: ['./watchlists.component.css'],
})
export class WatchlistsComponent implements OnInit {
  readonly store = inject(WatchlistStore);
  private readonly catalog = inject(InstrumentCatalogService);
  private readonly liveQuotes = inject(LiveQuoteStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly requestedId = signal<number | null>(null);
  newName = '';
  readonly creating = signal(false);
  /** Watchlist awaiting delete confirmation */
  readonly confirmDeleteId = signal<number | null>(null);
  /** Item ids with a remove request in flight */
  readonly removingIds = signal<ReadonlySet<number>>(new Set());
  readonly feedback = signal<{ text: string; error: boolean } | null>(null);

  /** The list shown on the right: the one in ?id=, else the first */
  readonly selected = computed<WatchlistResponse | null>(() => {
    const lists = this.store.watchlists();
    return lists.find((w) => w.watchlistId === this.requestedId()) ?? lists[0] ?? null;
  });

  readonly rows = computed<WatchlistRow[]>(() =>
    (this.selected()?.items ?? []).map((item) => {
      const instrument = this.catalog.find(item.symbol);
      const entry = this.liveQuotes.entry(item.symbol);
      const quote = entry?.quote ?? null;
      const currency = quote?.currency ?? instrument?.currency ?? '';
      return {
        item,
        name: instrument?.name ?? item.symbol,
        assetClass: instrument?.assetClass ?? '—',
        currency,
        currencySymbol: currencySymbol(currency),
        tradable: instrument?.tradable ?? false,
        price: quote?.price ?? null,
        changePercent: quote?.changePercent ?? null,
        stale: quote?.stale === true,
        receivedAt: quote?.receivedAt ?? null,
        priceStatus: entry?.status ?? 'loading',
        decimals: this.catalog.decimals(item.symbol),
      };
    }),
  );

  /** Symbols of the open watchlist, for the price alerts panel */
  readonly alertSymbols = computed(() => (this.selected()?.items ?? []).map((item) => item.symbol));

  constructor() {
    // Live prices for whichever watchlist is open
    this.liveQuotes.track(() => (this.selected()?.items ?? []).map((item) => item.symbol));
  }

  ngOnInit(): void {
    this.store.load();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = Number(params.get('id'));
      this.requestedId.set(Number.isFinite(id) && id > 0 ? id : null);
    });
  }

  select(watchlistId: number): void {
    this.confirmDeleteId.set(null);
    this.router.navigate([], { relativeTo: this.route, queryParams: { id: watchlistId }, replaceUrl: true });
  }

  createWatchlist(): void {
    const name = this.newName.trim();
    if (!name || this.creating()) {
      return;
    }
    this.creating.set(true);
    this.store.create(name).subscribe({
      next: (list) => {
        this.creating.set(false);
        this.newName = '';
        this.feedback.set({ text: `Created “${list.name}”.`, error: false });
        this.select(list.watchlistId);
      },
      error: (err: Error) => {
        this.creating.set(false);
        this.feedback.set({ text: err.message, error: true });
      },
    });
  }

  deleteWatchlist(watchlistId: number): void {
    if (this.confirmDeleteId() !== watchlistId) {
      this.confirmDeleteId.set(watchlistId);
      return;
    }
    const name = this.store.find(watchlistId)?.name ?? 'Watchlist';
    this.store.delete(watchlistId).subscribe({
      next: () => {
        this.confirmDeleteId.set(null);
        this.feedback.set({ text: `Deleted “${name}”.`, error: false });
        if (this.requestedId() === watchlistId) {
          this.router.navigate([], { relativeTo: this.route, queryParams: { id: null }, replaceUrl: true });
        }
      },
      error: (err: Error) => {
        this.confirmDeleteId.set(null);
        this.feedback.set({ text: err.message, error: true });
      },
    });
  }

  addInstrument(instrument: Instrument): void {
    const list = this.selected();
    if (!list) {
      return;
    }
    if (list.items.some((i) => i.symbol === instrument.symbol)) {
      this.feedback.set({ text: `${instrument.symbol} is already in “${list.name}”.`, error: true });
      return;
    }
    this.store.addSymbol(list.watchlistId, instrument.symbol).subscribe({
      next: () => this.feedback.set({ text: `Added ${instrument.symbol} to “${list.name}”.`, error: false }),
      error: (err: Error) => this.feedback.set({ text: err.message, error: true }),
    });
  }

  removeItem(row: WatchlistRow): void {
    const list = this.selected();
    if (!list) {
      return;
    }
    const id = row.item.watchlistItemId;
    this.removingIds.update((ids) => new Set(ids).add(id));
    const done = () =>
      this.removingIds.update((ids) => {
        const next = new Set(ids);
        next.delete(id);
        return next;
      });
    this.store.removeItem(list.watchlistId, id).subscribe({
      next: () => {
        done();
        this.feedback.set({ text: `Removed ${row.item.symbol} from “${list.name}”.`, error: false });
      },
      error: (err: Error) => {
        done();
        this.feedback.set({ text: err.message, error: true });
      },
    });
  }

  formatPrice(row: WatchlistRow): string {
    if (row.price === null) {
      return '—';
    }
    const amount = row.price.toLocaleString('en-US', { minimumFractionDigits: row.decimals, maximumFractionDigits: row.decimals });
    return `${row.currencySymbol}${amount}`;
  }

  /** Hover text for the price cell: where the price came from and how fresh it is */
  priceTitle(row: WatchlistRow): string {
    switch (row.priceStatus) {
      case 'loading':
        return 'Loading the latest price…';
      case 'missing':
        return 'No price yet. Prices are refreshed for instruments with an open position.';
      default: {
        if (!row.receivedAt) {
          return 'Price unavailable right now.';
        }
        const at = new Date(row.receivedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
        const note = row.priceStatus === 'error' ? ' (could not refresh, showing last price)' : '';
        return `Updated ${at}${row.stale ? ', delayed quote' : ''}${note}`;
      }
    }
  }
}
