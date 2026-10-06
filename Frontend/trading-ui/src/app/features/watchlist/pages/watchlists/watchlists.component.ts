import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { WatchlistItemResponse, WatchlistResponse } from '../../../../api/generated/watchlist';
import { InstrumentSearchComponent } from '../../../instruments/components/instrument-search/instrument-search.component';
import { Instrument } from '../../../instruments/instrument.models';
import { InstrumentCatalogService } from '../../../instruments/services/instrument-catalog.service';
import { WatchlistStore } from '../../watchlist.store';

/** A watchlist row: the stored item plus catalogue details and a (sample) quote */
export interface WatchlistRow {
  item: WatchlistItemResponse;
  name: string;
  assetClass: string;
  currency: string;
  tradable: boolean;
  price: number;
  changePercent: number | null;
  decimals: number;
}

/**
 * Watchlists page: create and delete watchlists, add instruments via search,
 * remove instruments, and jump to the order ticket for any of them.
 * Prices are sample values from InstrumentCatalogService until live quotes exist.
 */
@Component({
  selector: 'app-watchlists',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, InstrumentSearchComponent],
  templateUrl: './watchlists.component.html',
  styleUrls: ['./watchlists.component.css'],
})
export class WatchlistsComponent implements OnInit {
  readonly store = inject(WatchlistStore);
  private readonly catalog = inject(InstrumentCatalogService);
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
      const quote = this.catalog.quote(item.symbol);
      return {
        item,
        name: instrument?.name ?? item.symbol,
        assetClass: instrument?.assetClass ?? '—',
        currency: instrument?.currency ?? '',
        tradable: instrument?.tradable ?? false,
        price: quote?.price ?? 0,
        changePercent: quote?.changePercent ?? null,
        decimals: this.catalog.decimals(item.symbol),
      };
    }),
  );

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
    return row.price.toLocaleString('en-US', { minimumFractionDigits: row.decimals, maximumFractionDigits: row.decimals });
  }
}
