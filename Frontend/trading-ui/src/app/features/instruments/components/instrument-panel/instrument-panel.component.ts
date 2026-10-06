import { Component, Input, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CHART_RANGES, ChartMode, ChartRange, MarketState } from '../../instrument.models';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';
import { InstrumentChartComponent } from '../instrument-chart/instrument-chart.component';
import { AddToWatchlistComponent } from '../../../watchlist/components/add-to-watchlist/add-to-watchlist.component';

const MARKET_STATE_LABEL: Record<MarketState, string> = {
  open: 'Market open',
  closed: 'Market closed',
  pre: 'Pre-market',
  post: 'After hours',
  unknown: 'Status unknown',
};

/**
 * Instrument details for one symbol: latest quote (price, change vs previous close,
 * bid/ask, market state, freshness), a daily candle chart with volume, and key stats.
 * Market data comes from InstrumentCatalogService (a real INFY.NS snapshot, sample data
 * for everything else) until a market-data endpoint exists.
 */
@Component({
  selector: 'app-instrument-panel',
  standalone: true,
  imports: [CommonModule, InstrumentChartComponent, AddToWatchlistComponent],
  template: `
    @if (instrument(); as inst) {
      <section class="panel instrument-panel" data-testid="instrument-panel">
        <header class="instrument-header">
          <div class="identity">
            <span class="asset-icon" [attr.data-asset]="inst.assetClass">
              <i class="fas" [ngClass]="assetIcon()"></i>
            </span>
            <div>
              <h2 class="symbol" data-testid="instrument-symbol">{{ inst.symbol }}</h2>
              <p class="name">{{ inst.name }}</p>
            </div>
          </div>
          <div class="tags">
            <app-add-to-watchlist [symbol]="inst.symbol" />
            @if (!inst.tradable) {
              <span class="tag tag-blocked" role="note" data-testid="not-tradable-note"
                [title]="inst.symbol + ' is currently not tradable. Orders for it will be rejected.'">
                <i class="fas fa-ban"></i> Not tradable
              </span>
            }
            <span class="tag">{{ inst.assetClass }}</span>
            <span class="tag">{{ inst.currency }}</span>
            @if (source() === 'snapshot') {
              <span class="tag tag-snapshot" data-testid="data-source"
                title="Real daily candles captured from Fauxnance. Not a live feed.">
                <i class="fas fa-database"></i> Fauxnance snapshot
              </span>
            } @else {
              <span class="tag tag-sample" data-testid="data-source"
                title="Prices and chart are generated sample data until live market data is connected">
                <i class="fas fa-flask"></i> Sample data
              </span>
            }
          </div>
        </header>

        @if (quote(); as q) {
          <div class="quote-row">
            <div class="price-block">
              <span class="last" data-testid="instrument-last">{{ format(q.price) }}</span>
              <span class="currency">{{ q.currency }}</span>
              @if (q.change !== null) {
                <span class="change" [class.up]="q.change >= 0" [class.down]="q.change < 0" data-testid="instrument-change">
                  <i class="fas" [class.fa-caret-up]="q.change >= 0" [class.fa-caret-down]="q.change < 0"></i>
                  {{ q.change >= 0 ? '+' : '' }}{{ format(q.change) }} ({{ q.change >= 0 ? '+' : '' }}{{ q.changePercent | number: '1.2-2' }}%)
                </span>
              }
            </div>
            <div class="status-block">
              <span class="market-state" [attr.data-state]="q.marketState" data-testid="market-state">
                <span class="state-dot"></span>{{ marketStateLabel(q.marketState) }}
              </span>
              @if (q.stale) {
                <span class="stale" data-testid="quote-stale" title="This quote is past its freshness window">
                  <i class="fas fa-clock"></i> Delayed
                </span>
              }
            </div>
          </div>

          <div class="book" data-testid="quote-book">
            <span>Bid <b>{{ format(q.bid) }}</b></span>
            <span>Ask <b>{{ format(q.ask) }}</b></span>
            <span>Spread <b>{{ format(q.ask - q.bid) }}</b> <small>({{ spreadPercent() | number: '1.2-2' }}%)</small></span>
            <span class="as-of" data-testid="quote-as-of">as of {{ asOfLabel() }}</span>
          </div>
        }

        <div class="chart-toolbar">
          <div class="range-tabs" role="tablist" aria-label="Chart range">
            @for (r of ranges; track r) {
              <button
                type="button"
                role="tab"
                class="range-tab"
                [class.active]="range() === r"
                [attr.aria-selected]="range() === r"
                (click)="range.set(r)"
                [attr.data-testid]="'range-' + r"
              >{{ r }}</button>
            }
          </div>
          @if (stats()?.rangeReturnPercent; as ret) {
            <span class="range-return" [class.up]="ret >= 0" [class.down]="ret < 0" data-testid="range-return">
              {{ range() }} {{ ret >= 0 ? '+' : '' }}{{ ret | number: '1.2-2' }}%
            </span>
          }
          <div class="mode-toggle" role="radiogroup" aria-label="Chart type">
            <button type="button" role="radio" class="mode-btn" [class.active]="mode() === 'candles'"
              [attr.aria-checked]="mode() === 'candles'" (click)="mode.set('candles')" title="Candlesticks" data-testid="mode-candles">
              <i class="fas fa-chart-column"></i><span class="sr-only">Candlesticks</span>
            </button>
            <button type="button" role="radio" class="mode-btn" [class.active]="mode() === 'line'"
              [attr.aria-checked]="mode() === 'line'" (click)="mode.set('line')" title="Line" data-testid="mode-line">
              <i class="fas fa-chart-line"></i><span class="sr-only">Line</span>
            </button>
          </div>
        </div>

        <app-instrument-chart [series]="series()" [range]="range()" [decimals]="decimals()" [mode]="mode()" />

        @if (stats(); as s) {
          <dl class="stats" data-testid="instrument-stats">
            <div [title]="sessionTitle()"><dt>Prev close</dt><dd>{{ s.previousClose !== null ? format(s.previousClose) : '—' }}</dd></div>
            <div [title]="sessionTitle()">
              <dt>Prev day range</dt>
              <dd>{{ s.previousLow !== null && s.previousHigh !== null ? format(s.previousLow) + ' – ' + format(s.previousHigh) : '—' }}</dd>
            </div>
            <div [title]="sessionTitle()"><dt>Prev volume</dt><dd>{{ s.previousVolume !== null ? (s.previousVolume | number) : '—' }}</dd></div>
            <div><dt>Avg vol (30D)</dt><dd>{{ s.averageVolume30d !== null ? (s.averageVolume30d | number) : '—' }}</dd></div>
            <div>
              <dt>52-week range</dt>
              <dd class="year-range">
                <span class="range-bar" [attr.aria-label]="'Current price is at ' + yearPosition() + '% of the 52-week range'">
                  <span class="range-marker" [style.left.%]="yearPosition()" data-testid="year-range-marker"></span>
                </span>
                <span class="range-ends"><span>{{ format(s.yearLow) }}</span><span>{{ format(s.yearHigh) }}</span></span>
              </dd>
            </div>
          </dl>
        }
      </section>
    } @else {
      <section class="panel instrument-panel empty" data-testid="instrument-panel-empty">
        <div class="empty-state">
          <i class="fas fa-chart-line"></i>
          @if (symbolSignal().trim()) {
            <p>No details for “{{ symbolSignal().trim().toUpperCase() }}”</p>
            <small>Search above to pick an instrument from the list.</small>
          } @else {
            <p>Search for an instrument</p>
            <small>Its details and price chart will appear here, with the order ticket alongside.</small>
          }
        </div>
      </section>
    }
  `,
  styles: [`
    :host {
      display: block;
    }

    .instrument-panel {
      /* Set by the order ticket so the card and the ticket share one fixed height */
      min-height: var(--trade-card-height, auto);
      padding: 22px 24px;
    }

    /* Matches the ticket's height, so centre the prompt in the space */
    .instrument-panel.empty {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
    }

    .instrument-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .identity {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .asset-icon {
      width: 46px;
      height: 46px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--r-md);
      background: var(--brand-soft);
      color: var(--brand);
      font-size: 18px;
    }

    .asset-icon[data-asset='FX'] { background: var(--positive-soft); color: var(--positive); }
    .asset-icon[data-asset='CRYPTO'] { background: var(--warning-soft); color: var(--warning); }

    .symbol {
      margin: 0;
      font-size: 22px;
      font-weight: 650;
      letter-spacing: -0.015em;
    }

    .name {
      margin: 2px 0 0;
      font-size: 14px;
      color: var(--ink-500);
    }

    .tags {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    /* The watchlist menu drops down over the chart, so the card must not clip it */
    .instrument-panel {
      overflow: visible;
    }

    .tag {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 26px;
      padding: 0 10px;
      border-radius: var(--r-pill);
      background: var(--surface-sunken);
      color: var(--ink-700);
      font-size: 12px;
      font-weight: 600;
    }

    .tag-sample {
      background: var(--warning-soft);
      color: var(--warning);
    }

    .tag-snapshot {
      background: var(--info-soft);
      color: var(--info);
    }

    .tag-blocked {
      background: var(--negative-soft);
      color: var(--negative);
    }

    /* ===== Quote ===== */
    .quote-row {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 12px 20px;
      flex-wrap: wrap;
      margin-top: 16px;
    }

    .price-block {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 10px;
    }

    .last {
      font-size: 34px;
      font-weight: 650;
      letter-spacing: -0.025em;
      font-variant-numeric: tabular-nums;
    }

    .currency {
      font-size: 13px;
      font-weight: 600;
      color: var(--ink-500);
    }

    .change {
      font-size: 15px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .change.up { color: var(--positive); }
    .change.down { color: var(--negative); }

    .status-block {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-bottom: 6px;
    }

    .market-state {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 24px;
      padding: 0 10px;
      border-radius: var(--r-pill);
      background: var(--surface-sunken);
      color: var(--ink-700);
      font-size: 12px;
      font-weight: 600;
    }

    .state-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--ink-400);
    }

    .market-state[data-state='open'] { background: var(--positive-soft); color: var(--positive); }
    .market-state[data-state='open'] .state-dot {
      background: var(--positive);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--positive) 25%, transparent);
    }
    .market-state[data-state='pre'] .state-dot,
    .market-state[data-state='post'] .state-dot { background: var(--warning); }

    .stale {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      height: 24px;
      padding: 0 9px;
      border-radius: var(--r-pill);
      background: var(--warning-soft);
      color: var(--warning);
      font-size: 12px;
      font-weight: 600;
    }

    .as-of {
      font-size: 12.5px;
      color: var(--ink-500);
    }

    .book {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 18px;
      margin-top: 8px;
      font-size: 12.5px;
      color: var(--ink-500);
    }

    .book b {
      color: var(--ink-900);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .book small {
      /* Angular drops the whitespace between </b> and <small>, so space it here */
      margin-left: 5px;
      font-size: 11.5px;
    }

    .book .as-of {
      margin-left: auto;
    }

    /* ===== Chart toolbar ===== */
    .chart-toolbar {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 16px 0 10px;
    }

    .range-tabs,
    .mode-toggle {
      display: inline-flex;
      gap: 4px;
      padding: 4px;
      border-radius: var(--r-md);
      background: var(--surface-sunken);
    }

    .mode-toggle {
      margin-left: auto;
    }

    .range-tab,
    .mode-btn {
      min-width: 40px;
      height: 30px;
      padding: 0 10px;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: var(--ink-500);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
    }

    .mode-btn {
      min-width: 34px;
      padding: 0 8px;
    }

    .range-tab:hover,
    .mode-btn:hover {
      color: var(--ink-900);
    }

    .range-tab.active,
    .mode-btn.active {
      background: var(--surface);
      color: var(--ink-900);
      box-shadow: 0 1px 3px rgba(16, 24, 40, 0.12);
    }

    .range-return {
      font-size: 13px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }

    .range-return.up { color: var(--positive); }
    .range-return.down { color: var(--negative); }

    /* ===== Stats ===== */
    .stats {
      display: grid;
      grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.55fr) minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.6fr);
      gap: 14px 16px;
      margin: 16px 0 0;
      padding-top: 16px;
      border-top: 1px solid var(--line);
    }

    .stats dt {
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--ink-500);
    }

    .stats dd {
      margin: 4px 0 0;
      font-size: 14.5px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .stats dd.year-range {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding-top: 5px;
      /* The marker can sit at either end of the bar; don't clip it */
      overflow: visible;
    }

    .range-ends {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      font-size: 11.5px;
      font-weight: 600;
      color: var(--ink-700);
    }

    .range-bar {
      position: relative;
      display: block;
      /* Inset by half the marker so it stays inside the cell at 0% and 100% */
      margin: 0 6px;
      height: 6px;
      border-radius: var(--r-pill);
      background: linear-gradient(90deg, var(--negative-soft), var(--surface-sunken), var(--positive-soft));
      border: 1px solid var(--line);
    }

    .range-marker {
      position: absolute;
      top: 50%;
      width: 12px;
      height: 12px;
      margin-left: -6px;
      transform: translateY(-50%);
      border: 2px solid var(--surface);
      border-radius: 50%;
      background: var(--brand);
      box-shadow: 0 0 0 1px var(--brand);
    }

    @media (max-width: 900px) {
      .stats {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .stats > div:last-child {
        grid-column: span 2;
      }

      .book .as-of {
        margin-left: 0;
      }
    }

    @media (max-width: 560px) {
      .chart-toolbar {
        flex-wrap: wrap;
      }
    }
  `],
})
export class InstrumentPanelComponent {
  private readonly catalog = inject(InstrumentCatalogService);

  readonly ranges = CHART_RANGES;
  readonly symbolSignal = signal('');
  readonly range = signal<ChartRange>('3M');
  readonly mode = signal<ChartMode>('candles');

  @Input() set symbol(value: string | null | undefined) {
    this.symbolSignal.set(value ?? '');
  }

  readonly instrument = computed(() => this.catalog.find(this.symbolSignal()));
  readonly source = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.dataSource(instrument.symbol) : 'sample';
  });
  readonly quote = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.quote(instrument.symbol) : null;
  });
  readonly stats = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.stats(instrument.symbol, this.range()) : null;
  });
  readonly decimals = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.decimals(instrument.symbol) : 2;
  });
  readonly series = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.candlesInRange(instrument.symbol, this.range()) : [];
  });

  /** Where the last close sits in the 52-week range, 0–100 */
  readonly yearPosition = computed(() => {
    const s = this.stats();
    if (!s || s.yearHigh === s.yearLow) {
      return 50;
    }
    return Math.round(((s.price - s.yearLow) / (s.yearHigh - s.yearLow)) * 100);
  });

  /** Tooltip naming the session the "Prev" figures belong to */
  readonly sessionTitle = computed(() => {
    const date = this.stats()?.sessionDate;
    if (!date) {
      return '';
    }
    const day = new Date(`${date}T00:00:00Z`).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
    return `Previous session: ${day}`;
  });

  readonly spreadPercent = computed(() => {
    const q = this.quote();
    return q && q.price ? ((q.ask - q.bid) / q.price) * 100 : 0;
  });

  /** "Jun 29, 3:30 PM IST" in the exchange's own time zone */
  readonly asOfLabel = computed(() => {
    const q = this.quote();
    const instrument = this.instrument();
    if (!q || !instrument) {
      return '';
    }
    const hours = this.catalog.exchangeHours(instrument);
    const timeZone = hours?.timeZone ?? 'UTC';
    const text = new Date(q.quoteAsOf).toLocaleString('en-US', {
      timeZone,
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
    return `${text} ${hours?.label ?? 'UTC'}`;
  });

  marketStateLabel(state: MarketState): string {
    return MARKET_STATE_LABEL[state] ?? MARKET_STATE_LABEL.unknown;
  }

  assetIcon(): string {
    switch (this.instrument()?.assetClass) {
      case 'FX':
        return 'fa-money-bill-transfer';
      case 'CRYPTO':
        return 'fa-coins';
      default:
        return 'fa-building';
    }
  }

  format(value: number): string {
    const d = this.decimals();
    return value.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }
}
