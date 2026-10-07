import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { InstrumentCatalogService } from '../../../instruments/services/instrument-catalog.service';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { currencySymbol } from '../../../../shared/currency';
import { WatchlistResponse } from '../../../../api/generated/watchlist';
import { WatchlistStore } from '../../watchlist.store';

/** Rows shown on the dashboard before "View all" */
const MAX_ROWS = 5;

/**
 * Dashboard card showing one watchlist (switchable with tabs) and its first few
 * instruments with live prices from the order service's quote cache. "View all" opens the
 * Watchlists page on that list.
 */
@Component({
  selector: 'app-watchlist-widget',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <header class="panel-header">
      <div>
        <h2 class="panel-title">Watchlist</h2>
        <p class="panel-subtitle">Instruments you follow</p>
      </div>
      <a class="panel-link" routerLink="/watchlists" [queryParams]="current() ? { id: current()!.watchlistId } : {}" data-testid="watchlist-view-all">
        View all <i class="fas fa-arrow-right"></i>
      </a>
    </header>

    @if (store.watchlists().length > 1) {
      <div class="tabs" role="tablist" aria-label="Watchlists">
        @for (list of store.watchlists(); track list.watchlistId) {
          <button
            type="button"
            role="tab"
            class="tab"
            [class.active]="current()?.watchlistId === list.watchlistId"
            [attr.aria-selected]="current()?.watchlistId === list.watchlistId"
            (click)="selectedId.set(list.watchlistId)"
          >{{ list.name }}</button>
        }
      </div>
    }

    <div class="widget-body">
      @if (store.isLoading() && !store.isLoaded()) {
        <div class="loading-state"><i class="fas fa-spinner fa-spin"></i><p>Loading…</p></div>
      } @else if (store.error() && !store.watchlists().length) {
        <div class="empty-state compact">
          <i class="fas fa-circle-exclamation"></i>
          <p>Watchlists unavailable</p>
          <small>{{ store.error() }}</small>
        </div>
      } @else if (!current()) {
        <div class="empty-state compact" data-testid="watchlist-widget-empty">
          <i class="fas fa-bookmark"></i>
          <p>No watchlists yet</p>
          <small><a routerLink="/watchlists">Create one</a> to follow instruments here.</small>
        </div>
      } @else {
        <ul class="rows">
          @for (row of rows(); track row.symbol) {
            <li>
              <a class="row" routerLink="/order-ticket" [queryParams]="{ symbol: row.symbol }" [title]="'Trade ' + row.symbol">
                <span class="who">
                  <span class="sym">{{ row.symbol }}</span>
                  <span class="name">{{ row.name }}</span>
                </span>
                <span class="quote" [title]="row.title">
                  <span class="price" [class.muted]="row.price === '—'">{{ row.price }}</span>
                  @if (row.changePercent !== null) {
                    <span class="chg" [class.up]="row.changePercent >= 0" [class.down]="row.changePercent < 0">
                      {{ row.changePercent >= 0 ? '+' : '' }}{{ row.changePercent | number: '1.2-2' }}%
                    </span>
                  }
                </span>
              </a>
            </li>
          } @empty {
            <li class="empty-state compact">
              <i class="fas fa-magnifying-glass-chart"></i>
              <p>“{{ current()!.name }}” is empty</p>
              <small>Use “Watchlist” on any instrument to add it.</small>
            </li>
          }
        </ul>
        @if (hiddenCount() > 0) {
          <a class="more" routerLink="/watchlists" [queryParams]="{ id: current()!.watchlistId }">+{{ hiddenCount() }} more</a>
        }
      }
    </div>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      min-height: 0;
    }

    .tabs {
      display: flex;
      gap: 6px;
      padding: 10px 16px 0;
      overflow-x: auto;
      scrollbar-width: none;
    }

    .tab {
      flex-shrink: 0;
      max-width: 140px;
      height: 28px;
      padding: 0 12px;
      border: 1px solid var(--line);
      border-radius: var(--r-pill);
      background: var(--surface);
      color: var(--ink-500);
      font-size: 12.5px;
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      cursor: pointer;
    }

    .tab.active {
      border-color: transparent;
      background: var(--brand-soft);
      color: var(--brand-strong);
    }

    .widget-body {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      padding: 6px 8px 10px;
    }

    .rows {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    /* Compact header and one-line rows so five fit in the dashboard's bottom row */
    .panel-header {
      padding: 12px 18px;
    }

    .panel-subtitle {
      display: none;
    }

    .row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 6px 10px;
      border-radius: var(--r-md);
      color: inherit;
      text-decoration: none;
    }

    .row:hover {
      background: var(--surface-sunken);
      color: inherit;
    }

    .who,
    .quote {
      display: flex;
      align-items: baseline;
      gap: 8px;
      min-width: 0;
    }

    .quote {
      flex-shrink: 0;
    }

    .chg {
      min-width: 52px;
      text-align: right;
    }

    .sym {
      flex-shrink: 0;
      font-weight: 600;
      color: var(--ink-900);
    }

    .name {
      font-size: 12px;
      color: var(--ink-500);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .price {
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      color: var(--ink-900);
    }

    .price.muted {
      color: var(--ink-400, var(--ink-500));
      font-weight: 500;
    }

    .chg {
      font-size: 12px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .up { color: var(--positive); }
    .down { color: var(--negative); }

    .more {
      display: block;
      padding: 6px 12px 0;
      font-size: 12.5px;
      font-weight: 600;
      color: var(--brand-strong);
    }

    .empty-state.compact {
      padding: 28px 16px;
    }
  `],
})
export class WatchlistWidgetComponent implements OnInit {
  readonly store = inject(WatchlistStore);
  private readonly catalog = inject(InstrumentCatalogService);
  private readonly liveQuotes = inject(LiveQuoteStore);

  readonly selectedId = signal<number | null>(null);

  readonly current = computed<WatchlistResponse | null>(() => {
    const lists = this.store.watchlists();
    return lists.find((w) => w.watchlistId === this.selectedId()) ?? lists[0] ?? null;
  });

  readonly rows = computed(() =>
    (this.current()?.items ?? []).slice(0, MAX_ROWS).map((item) => {
      const entry = this.liveQuotes.entry(item.symbol);
      const quote = entry?.quote ?? null;
      const decimals = this.catalog.decimals(item.symbol);
      const instrument = this.catalog.find(item.symbol);
      const symbol = currencySymbol(quote?.currency ?? instrument?.currency);
      return {
        symbol: item.symbol,
        name: instrument?.name ?? '',
        price: quote
          ? symbol + quote.price.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
          : entry?.status === 'loading' || !entry ? '…' : '—',
        changePercent: quote?.changePercent ?? null,
        title: quote
          ? `Updated ${new Date(quote.receivedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })}`
          : entry?.status === 'missing' ? 'No price yet' : '',
      };
    }),
  );

  constructor() {
    // Live prices only for the rows on screen
    this.liveQuotes.track(() => (this.current()?.items ?? []).slice(0, MAX_ROWS).map((item) => item.symbol));
  }

  readonly hiddenCount = computed(() => Math.max(0, (this.current()?.items.length ?? 0) - MAX_ROWS));

  ngOnInit(): void {
    this.store.load();
  }
}
