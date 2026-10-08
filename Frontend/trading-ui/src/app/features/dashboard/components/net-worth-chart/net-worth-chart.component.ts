import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import type { ChartConfiguration, ChartData, ScriptableContext } from 'chart.js';
import { currencySymbol } from '../../../../shared/currency';
import type { NetWorthPoint } from '../../services/dashboard.service';

export type { NetWorthPoint };

export type NetWorthRange = '1W' | '1M' | '3M';

interface RangeOption {
  id: NetWorthRange;
  days: number;
  label: string;
}

export const NET_WORTH_RANGES: readonly RangeOption[] = [
  { id: '1W', days: 7, label: 'past week' },
  { id: '1M', days: 30, label: 'past month' },
  { id: '3M', days: 90, label: 'past 3 months' },
];

const DAY_MS = 86_400_000;

/**
 * The snapshot history with today's live value as its last point. Snapshots are taken at the end
 * of each UTC day, so today's is not there yet; if it is (the job already ran), the live value wins.
 */
export function withLiveValue(history: readonly NetWorthPoint[], liveValue: number | null, today: Date): NetWorthPoint[] {
  if (liveValue === null || !Number.isFinite(liveValue)) {
    return [...history];
  }
  const day = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
  return [...history.filter((p) => p.date.getTime() < day), { date: new Date(day), value: liveValue }];
}

/**
 * Dashboard net worth chart: the account's end-of-day net worth snapshots (order-service
 * account history), ending at today's live portfolio value.
 */
@Component({
  selector: 'app-net-worth-chart',
  standalone: true,
  imports: [BaseChartDirective],
  template: `
    <header class="nw-head">
      <div class="nw-heading">
        <h2 class="nw-title">Net worth</h2>
        @if (hasChart()) {
          <div class="nw-figures">
            <span class="nw-value" data-testid="net-worth-value">{{ formatMoney(latest() ?? 0) }}</span>
            <span class="nw-change" [class.up]="change() >= 0" [class.down]="change() < 0" data-testid="net-worth-change">
              <i class="fas" [class.fa-arrow-trend-up]="change() >= 0" [class.fa-arrow-trend-down]="change() < 0" aria-hidden="true"></i>
              {{ change() >= 0 ? '+' : '−' }}{{ formatMoney(Math.abs(change())) }}
              ({{ change() >= 0 ? '+' : '−' }}{{ Math.abs(changePercent()).toFixed(2) }}%)
            </span>
            <span class="nw-period">{{ rangeOption().label }}</span>
          </div>
        } @else if (latest() !== null) {
          <div class="nw-figures">
            <span class="nw-value" data-testid="net-worth-value">{{ formatMoney(latest()!) }}</span>
          </div>
        }
      </div>
      <div class="nw-ranges" role="group" aria-label="Chart range">
        @for (option of ranges; track option.id) {
          <button
            type="button"
            class="nw-range"
            [class.active]="range() === option.id"
            [attr.aria-pressed]="range() === option.id"
            (click)="range.set(option.id)"
          >{{ option.id }}</button>
        }
      </div>
    </header>
    <div class="nw-chart">
      @if (hasChart()) {
        <canvas baseChart type="line" [data]="chartData()" [options]="chartOptions()" aria-label="Net worth over time" role="img"></canvas>
      } @else {
        <p class="nw-state" data-testid="net-worth-state">
          @if (failed()) {
            <i class="fas fa-triangle-exclamation" aria-hidden="true"></i> Net worth history is unavailable right now.
          } @else if (history() === null) {
            Loading net worth history…
          } @else if (series().length < 2) {
            Your net worth history builds up from a snapshot taken at the end of each day. Check back tomorrow.
          } @else {
            Not enough history for the {{ rangeOption().label }} yet. Try a longer range.
          }
        </p>
      }
    </div>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      min-height: 0;
      height: 100%;
    }

    .nw-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
      padding: 14px 20px 6px;
    }

    .nw-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 13px;
      font-weight: 600;
      color: var(--ink-500);
    }

    .nw-figures {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 4px 12px;
      margin-top: 2px;
    }

    .nw-value {
      font-family: var(--font-display);
      font-size: 24px;
      font-weight: 700;
      letter-spacing: -0.02em;
      color: var(--ink-900);
      font-variant-numeric: tabular-nums;
    }

    .nw-change {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 13px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .nw-change.up { color: var(--positive); }
    .nw-change.down { color: var(--negative); }

    .nw-period {
      font-size: 12.5px;
      color: var(--ink-500);
    }

    .nw-ranges {
      display: inline-flex;
      gap: 2px;
      padding: 3px;
      border-radius: var(--r-pill);
      background: var(--surface-sunken);
    }

    .nw-range {
      min-width: 38px;
      height: 26px;
      padding: 0 10px;
      border: 0;
      border-radius: var(--r-pill);
      background: transparent;
      color: var(--ink-500);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: background-color var(--t-fast), color var(--t-fast);
    }

    .nw-range:hover {
      color: var(--ink-900);
    }

    .nw-range.active {
      background: var(--surface);
      color: var(--brand-strong);
      box-shadow: var(--shadow-card);
    }

    .nw-state {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      height: 100%;
      min-height: 120px;
      margin: 0;
      padding: 0 24px;
      text-align: center;
      font-size: 13px;
      color: var(--ink-500);
    }

    .nw-chart {
      position: relative;
      flex: 1;
      min-height: 0;
      padding: 4px 14px 10px 8px;
    }
  `],
})
export class NetWorthChartComponent {
  /** End-of-day snapshots, oldest first; null while loading */
  readonly history = input<readonly NetWorthPoint[] | null>(null);
  /** True when the history request failed */
  readonly failed = input(false);
  /** The account's live total value (cash + positions), shown as today's point */
  readonly currentValue = input<number | null>(null);
  /** ISO 4217 code of the value */
  readonly currency = input<string | null>(null);

  readonly ranges = NET_WORTH_RANGES;
  readonly range = signal<NetWorthRange>('3M');
  protected readonly Math = Math;

  /** Bumped when the theme changes so the chart picks up the new colours */
  private readonly themeVersion = signal(0);

  /** Snapshots plus today's live value */
  readonly series = computed(() => withLiveValue(this.history() ?? [], this.currentValue(), new Date()));

  readonly rangeOption = computed(() => NET_WORTH_RANGES.find((r) => r.id === this.range()) ?? NET_WORTH_RANGES[2]);

  /** Points on or after the first day of the range; history can have gaps, so filter by date */
  readonly points = computed(() => {
    const series = this.series();
    const now = new Date();
    const firstDay = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()) - (this.rangeOption().days - 1) * DAY_MS;
    return series.filter((p) => p.date.getTime() >= firstDay);
  });

  /** A line needs two points; anything less shows a message instead */
  readonly hasChart = computed(() => !this.failed() && this.history() !== null && this.points().length >= 2);

  /** Most recent value (today's live value when loaded), null when there is none */
  readonly latest = computed(() => {
    const series = this.series();
    return series.length ? series[series.length - 1].value : null;
  });
  readonly change = computed(() => {
    const points = this.points();
    return points.length ? points[points.length - 1].value - points[0].value : 0;
  });
  readonly changePercent = computed(() => {
    const first = this.points()[0]?.value ?? 0;
    return first ? (this.change() / first) * 100 : 0;
  });

  readonly chartData = computed<ChartData<'line'>>(() => {
    this.themeVersion();
    const colors = themeColors();
    return {
      // Full date for the tooltip; the axis shortens it (see x.ticks.callback)
      labels: this.points().map((p) => p.date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })),
      datasets: [
        {
          data: this.points().map((p) => p.value),
          borderColor: colors.line,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
          pointHoverBackgroundColor: colors.line,
          pointHoverBorderColor: colors.surface,
          pointHoverBorderWidth: 2,
          tension: 0.35,
          fill: 'start',
          backgroundColor: (ctx: ScriptableContext<'line'>) => {
            const { chart } = ctx;
            const area = chart.chartArea;
            if (!area) {
              return colors.fillTop;
            }
            const gradient = chart.ctx.createLinearGradient(0, area.top, 0, area.bottom);
            gradient.addColorStop(0, colors.fillTop);
            gradient.addColorStop(1, colors.fillBottom);
            return gradient;
          },
        },
      ],
    };
  });

  readonly chartOptions = computed<ChartConfiguration<'line'>['options']>(() => {
    this.themeVersion();
    const colors = themeColors();
    const points = this.points();
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 400 },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          displayColors: false,
          callbacks: { label: (item) => this.formatMoney(Number(item.parsed.y)) },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { display: false },
          ticks: {
            color: colors.tick,
            maxTicksLimit: 6,
            maxRotation: 0,
            autoSkipPadding: 16,
            font: { size: 11 },
            // Day and month, e.g. "Oct 6"
            callback: (_value, index) =>
              points[index]?.date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' }) ?? '',
          },
        },
        y: {
          grid: { color: colors.grid },
          border: { display: false },
          ticks: {
            color: colors.tick,
            maxTicksLimit: 4,
            font: { size: 11 },
            callback: (value) => this.formatCompact(Number(value)),
          },
        },
      },
    };
  });

  constructor() {
    // Re-colour the chart when the light/dark theme changes
    if (typeof MutationObserver !== 'undefined' && typeof document !== 'undefined') {
      const observer = new MutationObserver(() => this.themeVersion.update((v) => v + 1));
      observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
      inject(DestroyRef).onDestroy(() => observer.disconnect());
    }
  }

  formatMoney(value: number): string {
    const amount = value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${currencySymbol(this.currency())}${amount}`;
  }

  formatCompact(value: number): string {
    const amount = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
    return `${currencySymbol(this.currency())}${amount}`;
  }
}

/** Chart colours from the design tokens, so light and dark themes both work */
function themeColors(): { line: string; fillTop: string; fillBottom: string; grid: string; tick: string; surface: string } {
  const css = typeof document !== 'undefined' ? getComputedStyle(document.documentElement) : null;
  const token = (name: string, fallback: string) => css?.getPropertyValue(name).trim() || fallback;
  const line = token('--brand', '#2563eb');
  return {
    line,
    fillTop: withAlpha(line, 0.28),
    fillBottom: withAlpha(line, 0),
    grid: token('--line', '#e5e7eb'),
    tick: token('--ink-500', '#64748b'),
    surface: token('--surface', '#ffffff'),
  };
}

/** #rrggbb (or #rgb) to rgba(); other formats are returned unchanged */
function withAlpha(color: string, alpha: number): string {
  const hex = color.replace('#', '');
  if (!/^([0-9a-f]{3}|[0-9a-f]{6})$/i.test(hex)) {
    return color;
  }
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex;
  const n = parseInt(full, 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
}
