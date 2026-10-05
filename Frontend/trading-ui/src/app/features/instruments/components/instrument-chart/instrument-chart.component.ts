import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartRange, PricePoint } from '../../instrument.models';

const W = 1000;
const H = 300;
const PAD_TOP = 12;
const PAD_BOTTOM = 8;

/**
 * Placeholder price chart (SVG line + area with a hover crosshair).
 * Takes a plain price series, so it can be swapped for a charting library
 * (e.g. Chart.js via ng2-charts) without changing the pages that use it.
 */
@Component({
  selector: 'app-instrument-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (points().length > 1) {
      <div class="chart" [class.down]="!isUp()" data-testid="instrument-chart">
        <div class="plot">
          <div class="canvas" (mousemove)="onMove($event)" (mouseleave)="hoverIndex.set(null)">
          <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <linearGradient [attr.id]="gradientId" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="currentColor" stop-opacity="0.22" />
                <stop offset="100%" stop-color="currentColor" stop-opacity="0" />
              </linearGradient>
            </defs>
            @for (line of gridLines(); track line.y) {
              <line class="grid" x1="0" [attr.x2]="width" [attr.y1]="line.y" [attr.y2]="line.y" />
            }
            <path class="area" [attr.d]="areaPath()" [attr.fill]="'url(#' + gradientId + ')'" />
            <path class="line" [attr.d]="linePath()" data-testid="chart-line" />
          </svg>

          @if (hover(); as h) {
            <div class="crosshair" [style.left.%]="h.xPct"></div>
            <div class="dot" [style.left.%]="h.xPct" [style.top.%]="h.yPct"></div>
            <div class="tooltip" [style.left.%]="h.xPct" [class.flip]="h.xPct > 70" data-testid="chart-tooltip">
              <strong>{{ h.price }}</strong>
              <span>{{ h.time }}</span>
            </div>
          }
          </div>

          @for (line of gridLines(); track line.y) {
            <span class="y-label" [style.top.%]="(line.y / height) * 100">{{ line.label }}</span>
          }
        </div>

        <div class="x-labels">
          @for (label of xLabels(); track $index) {
            <span>{{ label }}</span>
          }
        </div>
      </div>
    } @else {
      <div class="chart-empty">No price history to display.</div>
    }
  `,
  styles: [`
    :host {
      display: block;
    }

    .chart {
      color: var(--positive);
    }

    .chart.down {
      color: var(--negative);
    }

    .plot {
      position: relative;
      height: 300px;
      padding-right: 64px;
      cursor: crosshair;
    }

    .canvas {
      position: relative;
      height: 100%;
    }

    svg {
      display: block;
      width: 100%;
      height: 100%;
      overflow: visible;
    }

    .grid {
      stroke: var(--line);
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
      stroke-dasharray: 3 4;
    }

    .line {
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
      stroke-linejoin: round;
    }

    .y-label {
      position: absolute;
      right: 0;
      transform: translateY(-50%);
      font-size: 11.5px;
      color: var(--ink-500);
      font-variant-numeric: tabular-nums;
      background: var(--surface);
      padding-left: 6px;
    }

    .crosshair {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 1px;
      background: var(--line-strong);
      pointer-events: none;
    }

    .dot {
      position: absolute;
      width: 10px;
      height: 10px;
      margin: -5px 0 0 -5px;
      border: 2px solid var(--surface);
      border-radius: 50%;
      background: currentColor;
      box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 25%, transparent);
      pointer-events: none;
    }

    .tooltip {
      position: absolute;
      top: 6px;
      margin-left: 12px;
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 8px 10px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--surface);
      box-shadow: var(--shadow-raised);
      white-space: nowrap;
      pointer-events: none;
    }

    .tooltip.flip {
      transform: translateX(calc(-100% - 24px));
    }

    .tooltip strong {
      font-size: 14px;
      color: var(--ink-900);
      font-variant-numeric: tabular-nums;
    }

    .tooltip span {
      font-size: 12px;
      color: var(--ink-500);
    }

    .x-labels {
      display: flex;
      justify-content: space-between;
      padding: 10px 64px 0 0;
      font-size: 11.5px;
      color: var(--ink-500);
    }

    .chart-empty {
      padding: 48px 0;
      text-align: center;
      color: var(--ink-500);
    }
  `],
})
export class InstrumentChartComponent {
  private static nextId = 0;

  readonly width = W;
  readonly height = H;
  readonly gradientId = `chart-fill-${InstrumentChartComponent.nextId++}`;

  readonly points = signal<PricePoint[]>([]);
  readonly rangeSignal = signal<ChartRange>('1D');
  readonly decimalsSignal = signal(2);
  readonly hoverIndex = signal<number | null>(null);

  @Input() set series(value: PricePoint[] | null | undefined) {
    this.points.set(value ?? []);
    this.hoverIndex.set(null);
  }

  @Input() set range(value: ChartRange) {
    this.rangeSignal.set(value);
  }

  @Input() set decimals(value: number) {
    this.decimalsSignal.set(value);
  }

  readonly isUp = computed(() => {
    const p = this.points();
    return p.length < 2 || p[p.length - 1].price >= p[0].price;
  });

  /** Price bounds with a little headroom so the line never touches the edges */
  private readonly bounds = computed(() => {
    const prices = this.points().map((p) => p.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const pad = (max - min || max * 0.01 || 1) * 0.08;
    return { min: min - pad, max: max + pad };
  });

  private x(index: number): number {
    const n = this.points().length;
    return n > 1 ? (index / (n - 1)) * W : 0;
  }

  private y(price: number): number {
    const { min, max } = this.bounds();
    return PAD_TOP + (1 - (price - min) / (max - min)) * (H - PAD_TOP - PAD_BOTTOM);
  }

  readonly linePath = computed(() =>
    this.points()
      .map((p, i) => `${i === 0 ? 'M' : 'L'}${this.x(i).toFixed(2)},${this.y(p.price).toFixed(2)}`)
      .join(' ')
  );

  readonly areaPath = computed(() => {
    const line = this.linePath();
    return line ? `${line} L${W},${H} L0,${H} Z` : '';
  });

  readonly gridLines = computed(() => {
    const { min, max } = this.bounds();
    const steps = 4;
    return Array.from({ length: steps }, (_, i) => {
      const price = max - ((i + 0.5) / steps) * (max - min);
      return { y: this.y(price), label: this.formatPrice(price) };
    });
  });

  readonly xLabels = computed(() => {
    const p = this.points();
    if (p.length < 2) {
      return [];
    }
    const idx = [0, Math.round((p.length - 1) / 3), Math.round((2 * (p.length - 1)) / 3), p.length - 1];
    return idx.map((i) => this.formatAxisTime(p[i].time));
  });

  readonly hover = computed(() => {
    const i = this.hoverIndex();
    const p = this.points();
    if (i === null || !p[i]) {
      return null;
    }
    return {
      xPct: (this.x(i) / W) * 100,
      yPct: (this.y(p[i].price) / H) * 100,
      price: this.formatPrice(p[i].price),
      time: this.formatTooltipTime(p[i].time),
    };
  });

  onMove(event: MouseEvent): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!rect.width) {
      return;
    }
    const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
    this.hoverIndex.set(Math.round(ratio * (this.points().length - 1)));
  }

  private formatPrice(value: number): string {
    const d = this.decimalsSignal();
    return value.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }

  private formatAxisTime(time: Date): string {
    switch (this.rangeSignal()) {
      case '1D':
        return time.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
      case '1W':
        return time.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric' });
      case '1Y':
        return time.toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
      default:
        return time.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    }
  }

  private formatTooltipTime(time: Date): string {
    return this.rangeSignal() === '1D' || this.rangeSignal() === '1W'
      ? time.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
      : time.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  }
}
