import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import type { ChartConfiguration, ChartData, ScriptableContext } from 'chart.js';
import { currencySymbol } from '../../../../shared/currency';

export type NetWorthRange = '1W' | '1M' | '3M' | '6M' | '1Y';

interface RangeOption {
  id: NetWorthRange;
  days: number;
  label: string;
}

export const NET_WORTH_RANGES: readonly RangeOption[] = [
  { id: '1W', days: 7, label: 'past week' },
  { id: '1M', days: 30, label: 'past month' },
  { id: '3M', days: 90, label: 'past 3 months' },
  { id: '6M', days: 180, label: 'past 6 months' },
  { id: '1Y', days: 365, label: 'past year' },
];

/** Shown when the real portfolio value has not loaded */
const SAMPLE_END_VALUE = 125_000;
const HISTORY_DAYS = 365;
const DAY_MS = 86_400_000;

export interface NetWorthPoint {
  date: Date;
  value: number;
}

/**
 * Sample net worth history: a seeded random walk with a gentle upward drift, scaled so the last
 * point equals `endValue`. Deterministic, so the chart does not change between visits.
 */
export function sampleNetWorthHistory(endValue: number, today: Date, days = HISTORY_DAYS): NetWorthPoint[] {
  const random = mulberry32(14); // seed chosen for a gently rising history
  const raw: number[] = [];
  let value = 100;
  for (let i = 0; i < days; i++) {
    const drift = 0.0006;
    const shock = (random() - 0.5) * 0.022;
    value = Math.max(1, value * (1 + drift + shock));
    raw.push(value);
  }
  const scale = endValue / raw[raw.length - 1];
  const end = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
  return raw.map((v, i) => ({
    date: new Date(end - (days - 1 - i) * DAY_MS),
    value: Math.round(v * scale * 100) / 100,
  }));
}

/** Small seeded PRNG so the sample history is stable */
function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Dashboard net worth chart. The history is SAMPLE data (net worth is not recorded yet);
 * its last point is the account's real portfolio value once that has loaded.
 */
@Component({
  selector: 'app-net-worth-chart',
  standalone: true,
  imports: [BaseChartDirective],
  template: `
    <header class="nw-head">
      <div class="nw-heading">
        <h2 class="nw-title">
          Net worth
          <span class="sample-tag" title="Illustrative history: net worth is not recorded yet. Today's value is your portfolio value.">Sample</span>
        </h2>
        <div class="nw-figures">
          <span class="nw-value" data-testid="net-worth-value">{{ formatMoney(latest()) }}</span>
          <span class="nw-change" [class.up]="change() >= 0" [class.down]="change() < 0" data-testid="net-worth-change">
            <i class="fas" [class.fa-arrow-trend-up]="change() >= 0" [class.fa-arrow-trend-down]="change() < 0" aria-hidden="true"></i>
            {{ change() >= 0 ? '+' : '−' }}{{ formatMoney(Math.abs(change())) }}
            ({{ change() >= 0 ? '+' : '−' }}{{ Math.abs(changePercent()).toFixed(2) }}%)
          </span>
          <span class="nw-period">{{ rangeOption().label }}</span>
        </div>
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
      <canvas baseChart type="line" [data]="chartData()" [options]="chartOptions()" aria-label="Net worth over time" role="img"></canvas>
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

    .sample-tag {
      padding: 1px 7px;
      border-radius: var(--r-pill);
      background: var(--warning-soft);
      color: var(--warning);
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      cursor: help;
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

    .nw-chart {
      position: relative;
      flex: 1;
      min-height: 0;
      padding: 4px 14px 10px 8px;
    }
  `],
})
export class NetWorthChartComponent {
  /** The account's real total value (cash + positions); the sample history ends here */
  readonly currentValue = input<number | null>(null);
  /** ISO 4217 code of the value */
  readonly currency = input<string | null>(null);

  readonly ranges = NET_WORTH_RANGES;
  readonly range = signal<NetWorthRange>('3M');
  protected readonly Math = Math;

  /** Bumped when the theme changes so the chart picks up the new colours */
  private readonly themeVersion = signal(0);

  private readonly history = computed(() =>
    sampleNetWorthHistory(this.currentValue() ?? SAMPLE_END_VALUE, new Date()),
  );

  readonly rangeOption = computed(() => NET_WORTH_RANGES.find((r) => r.id === this.range()) ?? NET_WORTH_RANGES[2]);

  readonly points = computed(() => this.history().slice(-this.rangeOption().days));

  readonly latest = computed(() => this.points()[this.points().length - 1]?.value ?? 0);
  readonly change = computed(() => this.latest() - (this.points()[0]?.value ?? 0));
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
    const long = points.length > 90;
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
            // Day and month for short ranges, month and year for 6M / 1Y
            callback: (_value, index) => {
              const date = points[index]?.date;
              if (!date) {
                return '';
              }
              return long
                ? date.toLocaleDateString('en-US', { month: 'short', year: '2-digit', timeZone: 'UTC' }).replace(' ', " '")
                : date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
            },
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
