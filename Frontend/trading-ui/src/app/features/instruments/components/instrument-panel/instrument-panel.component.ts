import { Component, Input, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CHART_RANGES, ChartRange } from '../../instrument.models';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';
import { InstrumentChartComponent } from '../instrument-chart/instrument-chart.component';

/**
 * Instrument details + price chart for one symbol.
 * Market data is SAMPLE data from InstrumentCatalogService until a real feed exists.
 */
@Component({
  selector: 'app-instrument-panel',
  standalone: true,
  imports: [CommonModule, InstrumentChartComponent],
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
            <span class="tag">{{ inst.assetClass }}</span>
            <span class="tag">{{ inst.currency }}</span>
            <span class="tag tag-sample" title="Prices and chart are sample data until live market data is connected">
              <i class="fas fa-flask"></i> Sample data
            </span>
          </div>
        </header>

        @if (!inst.tradable) {
          <div class="alert alert-warning not-tradable" role="note" data-testid="not-tradable-note">
            <i class="fas fa-triangle-exclamation"></i>
            <span>{{ inst.symbol }} is currently not tradable. Orders for it will be rejected.</span>
          </div>
        }

        @if (quote(); as q) {
          <div class="price-row">
            <span class="last" data-testid="instrument-last">{{ format(q.last) }}</span>
            <span class="currency">{{ inst.currency }}</span>
            <span class="change" [class.up]="q.change >= 0" [class.down]="q.change < 0" data-testid="instrument-change">
              <i class="fas" [class.fa-caret-up]="q.change >= 0" [class.fa-caret-down]="q.change < 0"></i>
              {{ q.change >= 0 ? '+' : '' }}{{ format(q.change) }} ({{ q.change >= 0 ? '+' : '' }}{{ q.changePercent | number: '1.2-2' }}%)
            </span>
            <span class="as-of">today</span>
          </div>
        }

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

        <app-instrument-chart [series]="series()" [range]="range()" [decimals]="decimals()" />

        @if (quote(); as q) {
          <dl class="stats">
            <div><dt>Open</dt><dd>{{ format(q.open) }}</dd></div>
            <div><dt>Day high</dt><dd>{{ format(q.dayHigh) }}</dd></div>
            <div><dt>Day low</dt><dd>{{ format(q.dayLow) }}</dd></div>
            <div><dt>52W high</dt><dd>{{ format(q.yearHigh) }}</dd></div>
            <div><dt>52W low</dt><dd>{{ format(q.yearLow) }}</dd></div>
            <div><dt>Volume</dt><dd>{{ q.volume | number }}</dd></div>
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
      padding: 24px;
    }

    .instrument-panel.empty {
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
      width: 48px;
      height: 48px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--r-md);
      background: var(--brand-soft);
      color: var(--brand);
      font-size: 19px;
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
      gap: 6px;
      flex-wrap: wrap;
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

    .not-tradable {
      margin-top: 16px;
    }

    .price-row {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 20px;
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

    .as-of {
      font-size: 13px;
      color: var(--ink-400);
    }

    .range-tabs {
      display: inline-flex;
      gap: 4px;
      margin: 20px 0 12px;
      padding: 4px;
      border-radius: var(--r-md);
      background: var(--surface-sunken);
    }

    .range-tab {
      min-width: 44px;
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

    .range-tab:hover {
      color: var(--ink-900);
    }

    .range-tab.active {
      background: var(--surface);
      color: var(--ink-900);
      box-shadow: 0 1px 3px rgba(16, 24, 40, 0.12);
    }

    .stats {
      display: grid;
      grid-template-columns: repeat(6, minmax(0, 1fr));
      gap: 16px;
      margin: 20px 0 0;
      padding-top: 18px;
      border-top: 1px solid var(--line);
    }

    .stats dt {
      font-size: 11.5px;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--ink-500);
    }

    .stats dd {
      margin: 4px 0 0;
      font-size: 15px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    @media (max-width: 900px) {
      .stats {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }
  `],
})
export class InstrumentPanelComponent {
  private readonly catalog = inject(InstrumentCatalogService);

  readonly ranges = CHART_RANGES;
  readonly symbolSignal = signal('');
  readonly range = signal<ChartRange>('1D');

  @Input() set symbol(value: string | null | undefined) {
    this.symbolSignal.set(value ?? '');
  }

  readonly instrument = computed(() => this.catalog.find(this.symbolSignal()));
  readonly quote = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.quote(instrument.symbol) : null;
  });
  readonly decimals = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.decimals(instrument.symbol) : 2;
  });
  readonly series = computed(() => {
    const instrument = this.instrument();
    return instrument ? this.catalog.history(instrument.symbol, this.range()) : [];
  });

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
