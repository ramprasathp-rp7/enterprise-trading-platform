import { Component, Input, computed, signal } from '@angular/core';
import { QuotePoint } from '../../instrument.models';

const W = 1000;
const H = 300;
const PAD_TOP = 10;
const PAD_BOTTOM = 6;

/**
 * Intraday price line drawn in SVG from the day's cached quotes (one point per cache
 * refresh), with a previous-close baseline, a last-price marker and a hover tooltip
 * showing price, bid and ask. Times are shown in the exchange's time zone.
 */
@Component({
  selector: 'app-quotes-chart',
  standalone: true,
  template: `
    @if (points().length > 1) {
      <div class="chart" [class.down]="!isUp()" data-testid="quotes-chart">
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
              @if (baselineY(); as by) {
                <line class="baseline" x1="0" [attr.x2]="width" [attr.y1]="by" [attr.y2]="by" data-testid="quotes-baseline" />
              }

              <path class="area" [attr.d]="areaPath()" [attr.fill]="'url(#' + gradientId + ')'" />
              <path class="line" [attr.d]="linePath()" data-testid="quotes-line" />
              <line class="last-line" x1="0" [attr.x2]="width" [attr.y1]="lastY()" [attr.y2]="lastY()" />
            </svg>

            @if (hover(); as h) {
              <div class="crosshair" [style.left.%]="h.xPct"></div>
              <span class="dot" [style.left.%]="h.xPct" [style.top.%]="h.yPct"></span>
              <div class="tooltip" [style.left.%]="h.xPct" [class.flip]="h.xPct > 62" data-testid="quotes-tooltip">
                <div class="tt-head">
                  <span class="tt-time">{{ h.time }}</span>
                  @if (h.change) {
                    <span class="tt-change" [class.up]="h.changeUp" [class.down]="!h.changeUp">{{ h.change }}</span>
                  }
                </div>
                <dl class="tt-grid">
                  <dt>Price</dt><dd>{{ h.price }}</dd>
                  <dt>Bid</dt><dd>{{ h.bid }}</dd>
                  <dt>Ask</dt><dd>{{ h.ask }}</dd>
                </dl>
              </div>
            }
          </div>

          @for (line of axisLabels(); track line.y) {
            <span class="y-label" [style.top.%]="(line.y / height) * 100">{{ line.label }}</span>
          }
          <span class="last-tag" [style.top.%]="(lastY() / height) * 100" data-testid="quotes-last-tag">{{ lastLabel() }}</span>
        </div>

        <div class="x-labels">
          @for (label of xLabels(); track $index) {
            <span>{{ label }}</span>
          }
        </div>
      </div>
    } @else if (points().length === 1) {
      <div class="chart-empty" data-testid="quotes-chart-empty">
        One quote cached so far today ({{ lastLabel() }} at {{ formatTime(points()[0].time) }}). The line appears after the next refresh.
      </div>
    } @else {
      <div class="chart-empty" data-testid="quotes-chart-empty">No quotes cached today yet. They refresh every five minutes.</div>
    }
  `,
  styles: [`
    :host { display: block; }

    .chart { color: var(--positive); }
    .chart.down { color: var(--negative); }

    .plot {
      position: relative;
      height: var(--chart-height, 300px);
      padding-right: 72px;
      cursor: crosshair;
    }

    .canvas { position: relative; height: 100%; }

    svg { display: block; width: 100%; height: 100%; overflow: visible; }

    .grid {
      stroke: var(--line);
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
      stroke-dasharray: 3 4;
    }

    .baseline {
      stroke: var(--ink-400);
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
      stroke-dasharray: 2 3;
    }

    .line {
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
      stroke-linejoin: round;
    }

    .last-line {
      stroke: currentColor;
      stroke-width: 1;
      stroke-dasharray: 4 3;
      vector-effect: non-scaling-stroke;
      opacity: 0.7;
    }

    .last-tag {
      position: absolute;
      right: 0;
      transform: translateY(-50%);
      min-width: 64px;
      padding: 2px 6px;
      border-radius: 6px;
      background: var(--positive);
      color: #ffffff;
      font-size: 11.5px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      text-align: center;
    }

    .chart.down .last-tag { background: var(--negative); }

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
      width: 8px;
      height: 8px;
      margin: -4px 0 0 -4px;
      border: 2px solid var(--surface);
      border-radius: 50%;
      background: currentColor;
      pointer-events: none;
    }

    .tooltip {
      position: absolute;
      top: 6px;
      margin-left: 12px;
      min-width: 160px;
      padding: 10px 12px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--surface);
      box-shadow: var(--shadow-raised);
      white-space: nowrap;
      pointer-events: none;
      color: var(--ink-900);
      z-index: 2;
    }

    .tooltip.flip { transform: translateX(calc(-100% - 24px)); }

    .tt-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 6px;
      font-size: 12px;
    }

    .tt-time { font-weight: 600; color: var(--ink-700); }
    .tt-change { font-weight: 700; font-variant-numeric: tabular-nums; }
    .tt-change.up { color: var(--positive); }
    .tt-change.down { color: var(--negative); }

    .tt-grid {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 3px 10px;
      margin: 0;
      font-size: 12.5px;
      font-variant-numeric: tabular-nums;
    }

    .tt-grid dt { color: var(--ink-500); font-weight: 600; }
    .tt-grid dd { margin: 0; font-weight: 600; text-align: right; }

    .x-labels {
      display: flex;
      justify-content: space-between;
      padding: 10px 72px 0 0;
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
export class QuotesChartComponent {
  private static nextId = 0;

  readonly width = W;
  readonly height = H;
  readonly gradientId = `quotes-fill-${QuotesChartComponent.nextId++}`;

  readonly points = signal<QuotePoint[]>([]);
  readonly decimalsSignal = signal(2);
  readonly timeZoneSignal = signal('UTC');
  readonly zoneLabelSignal = signal('UTC');
  readonly previousCloseSignal = signal<number | null>(null);
  readonly hoverIndex = signal<number | null>(null);

  @Input() set quotes(value: QuotePoint[] | null | undefined) {
    this.points.set(value ?? []);
    this.hoverIndex.set(null);
  }

  @Input() set decimals(value: number) {
    this.decimalsSignal.set(value);
  }

  /** IANA zone the time axis is shown in */
  @Input() set timeZone(value: string | null | undefined) {
    this.timeZoneSignal.set(value || 'UTC');
  }

  /** Short zone name shown in the tooltip, e.g. IST */
  @Input() set zoneLabel(value: string | null | undefined) {
    this.zoneLabelSignal.set(value || 'UTC');
  }

  /** Drawn as a dotted baseline and used for the tooltip's change */
  @Input() set previousClose(value: number | null | undefined) {
    this.previousCloseSignal.set(value ?? null);
  }

  /** Up against the previous close when known, else against the day's first quote */
  readonly isUp = computed(() => {
    const p = this.points();
    const reference = this.previousCloseSignal() ?? p[0]?.price;
    return !p.length || reference === undefined || p[p.length - 1].price >= reference;
  });

  /** Price bounds over bid/ask, the line and the baseline, with headroom */
  private readonly bounds = computed(() => {
    const p = this.points();
    const values = p.flatMap((q) => [q.price, q.bid, q.ask]).filter(Number.isFinite);
    const baseline = this.previousCloseSignal();
    if (baseline !== null) {
      values.push(baseline);
    }
    const min = Math.min(...values);
    const max = Math.max(...values);
    const pad = (max - min || max * 0.002 || 1) * 0.08;
    return { min: min - pad, max: max + pad };
  });

  /** Time span on the x axis: first to last quote */
  private readonly span = computed(() => {
    const p = this.points();
    const start = Date.parse(p[0]?.time ?? '');
    const end = Date.parse(p[p.length - 1]?.time ?? '');
    return { start, length: Math.max(end - start, 1) };
  });

  private x(index: number): number {
    const { start, length } = this.span();
    return ((Date.parse(this.points()[index].time) - start) / length) * W;
  }

  private y(price: number): number {
    const { min, max } = this.bounds();
    return PAD_TOP + (1 - (price - min) / (max - min)) * (H - PAD_BOTTOM - PAD_TOP);
  }

  readonly linePath = computed(() =>
    this.points()
      .map((q, i) => `${i === 0 ? 'M' : 'L'}${this.x(i).toFixed(2)},${this.y(q.price).toFixed(2)}`)
      .join(' ')
  );

  readonly areaPath = computed(() => {
    const line = this.linePath();
    const n = this.points().length;
    const bottom = H - PAD_BOTTOM;
    return n ? `${line} L${this.x(n - 1).toFixed(2)},${bottom} L${this.x(0).toFixed(2)},${bottom} Z` : '';
  });

  readonly baselineY = computed(() => {
    const close = this.previousCloseSignal();
    return close === null || !this.points().length ? null : this.y(close);
  });

  readonly lastY = computed(() => {
    const p = this.points();
    return p.length ? this.y(p[p.length - 1].price) : 0;
  });

  readonly lastLabel = computed(() => {
    const p = this.points();
    return p.length ? this.formatPrice(p[p.length - 1].price) : '';
  });

  readonly gridLines = computed(() => {
    const { min, max } = this.bounds();
    const steps = 4;
    return Array.from({ length: steps }, (_, i) => {
      const price = max - ((i + 0.5) / steps) * (max - min);
      return { y: this.y(price), label: this.formatPrice(price) };
    });
  });

  /** Axis labels, minus any the last-price tag would cover */
  readonly axisLabels = computed(() => {
    const lastY = this.lastY();
    return this.gridLines().filter((line) => Math.abs(line.y - lastY) > H * 0.07);
  });

  /** Five evenly spaced times across the span */
  readonly xLabels = computed(() => {
    if (this.points().length < 2) {
      return [];
    }
    const { start, length } = this.span();
    return Array.from({ length: 5 }, (_, i) => this.formatTime(new Date(start + (i * length) / 4).toISOString(), false));
  });

  readonly hover = computed(() => {
    const i = this.hoverIndex();
    const q = i === null ? undefined : this.points()[i];
    if (i === null || !q) {
      return null;
    }
    const reference = this.previousCloseSignal();
    const changePct = reference ? ((q.price - reference) / reference) * 100 : null;
    return {
      xPct: (this.x(i) / W) * 100,
      yPct: (this.y(q.price) / H) * 100,
      time: this.formatTime(q.time),
      price: this.formatPrice(q.price),
      bid: this.formatPrice(q.bid),
      ask: this.formatPrice(q.ask),
      change: changePct === null ? '' : `${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%`,
      changeUp: (changePct ?? 0) >= 0,
    };
  });

  /** Nearest quote in time to the pointer */
  onMove(event: MouseEvent): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const p = this.points();
    if (!rect.width || !p.length) {
      return;
    }
    const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
    const target = ratio * W;
    let best = 0;
    for (let i = 1; i < p.length; i++) {
      if (Math.abs(this.x(i) - target) < Math.abs(this.x(best) - target)) {
        best = i;
      }
    }
    this.hoverIndex.set(best);
  }

  formatTime(iso: string, withZone = true): string {
    const text = new Date(iso).toLocaleTimeString('en-US', { timeZone: this.timeZoneSignal(), hour: 'numeric', minute: '2-digit' });
    return withZone ? `${text} ${this.zoneLabelSignal()}` : text;
  }

  private formatPrice(value: number): string {
    const d = this.decimalsSignal();
    return value.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }
}
