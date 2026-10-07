import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Candle, ChartMode, ChartRange } from '../../instrument.models';

const W = 1000;
const H = 300;
const PAD_TOP = 10;
/** Share of the height used by the price pane when volume is shown */
const PRICE_SHARE = 0.76;
const PANE_GAP = 10;

interface CandleShape {
  x: number;
  bodyX: number;
  bodyWidth: number;
  bodyY: number;
  bodyHeight: number;
  wickTop: number;
  wickBottom: number;
  up: boolean;
  synthetic: boolean;
  volumeY: number | null;
}

/**
 * Daily price chart drawn in SVG: candlesticks (or a close line), a volume pane,
 * a last-price marker and a hover tooltip with the full OHLCV bar.
 * Synthetic bars (provider gap-fills) are drawn hollow and dashed, without volume.
 */
@Component({
  selector: 'app-instrument-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (candles().length > 1) {
      <div class="chart" [class.down]="!isUp()" [class.line-mode]="modeSignal() === 'line'" data-testid="instrument-chart">
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

              @if (hasVolume()) {
                <line class="pane-divider" x1="0" [attr.x2]="width" [attr.y1]="volumeTop() - pane_gap / 2" [attr.y2]="volumeTop() - pane_gap / 2" />
                @for (s of shapes(); track $index) {
                  @if (s.volumeY !== null) {
                    <rect class="vol" [class.up]="s.up" [class.down]="!s.up"
                      [attr.x]="s.bodyX" [attr.y]="s.volumeY" [attr.width]="s.bodyWidth" [attr.height]="height - s.volumeY" />
                  }
                }
              }

              @if (modeSignal() === 'candles') {
                <g data-testid="chart-candles">
                  @for (s of shapes(); track $index) {
                    <g class="candle" [class.up]="s.up" [class.down]="!s.up" [class.synthetic]="s.synthetic">
                      <line class="wick" [attr.x1]="s.x" [attr.x2]="s.x" [attr.y1]="s.wickTop" [attr.y2]="s.wickBottom" />
                      <rect class="body" [attr.x]="s.bodyX" [attr.y]="s.bodyY" [attr.width]="s.bodyWidth" [attr.height]="s.bodyHeight" />
                    </g>
                  }
                </g>
              } @else {
                <path class="area" [attr.d]="areaPath()" [attr.fill]="'url(#' + gradientId + ')'" />
                <path class="line" [attr.d]="linePath()" data-testid="chart-line" />
              }

              <line class="last-line" x1="0" [attr.x2]="width" [attr.y1]="lastY()" [attr.y2]="lastY()" />
            </svg>

            @if (modeSignal() === 'line') {
              @for (s of shapes(); track $index) {
                @if (s.synthetic) {
                  <span class="gap-dot" [style.left.%]="(s.x / width) * 100" [style.top.%]="(closeY($index) / height) * 100"></span>
                }
              }
            }

            @if (hover(); as h) {
              <div class="crosshair" [style.left.%]="h.xPct"></div>
              <div class="tooltip" [style.left.%]="h.xPct" [class.flip]="h.xPct > 62" data-testid="chart-tooltip">
                <div class="tt-head">
                  <span class="tt-date">{{ h.date }}</span>
                  <span class="tt-change" [class.up]="h.changeUp" [class.down]="!h.changeUp">{{ h.change }}</span>
                </div>
                <dl class="tt-grid">
                  <dt>O</dt><dd>{{ h.open }}</dd>
                  <dt>H</dt><dd>{{ h.high }}</dd>
                  <dt>L</dt><dd>{{ h.low }}</dd>
                  <dt>C</dt><dd>{{ h.close }}</dd>
                </dl>
                <div class="tt-foot">
                  <span>Adj close <b>{{ h.adjclose }}</b></span>
                  <span>Vol <b>{{ h.volume }}</b></span>
                </div>
                @if (h.synthetic) {
                  <div class="tt-note" data-testid="tooltip-synthetic"><i class="fas fa-circle-info"></i> Estimated bar: no trades recorded</div>
                }
              </div>
            }
          </div>

          @for (line of axisLabels(); track line.y) {
            <span class="y-label" [style.top.%]="(line.y / height) * 100">{{ line.label }}</span>
          }
          <span class="last-tag" [style.top.%]="(lastY() / height) * 100" data-testid="chart-last-tag">{{ lastLabel() }}</span>
          @if (hasVolume()) {
            <span class="y-label vol-label" [style.top.%]="(volumeTop() / height) * 100">{{ maxVolumeLabel() }}</span>
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
      --up: var(--positive);
      --down: var(--negative);
      color: var(--positive);
    }

    .chart.down {
      color: var(--negative);
    }

    .plot {
      position: relative;
      height: var(--chart-height, 300px);
      padding-right: 72px;
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

    .grid,
    .pane-divider {
      stroke: var(--line);
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
      stroke-dasharray: 3 4;
    }

    .pane-divider {
      stroke-dasharray: none;
    }

    /* Candles */
    .wick {
      stroke-width: 1.25;
      vector-effect: non-scaling-stroke;
    }

    .body {
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
    }

    .candle.up .wick, .candle.up .body { stroke: var(--up); }
    .candle.up .body { fill: var(--up); }
    .candle.down .wick, .candle.down .body { stroke: var(--down); }
    .candle.down .body { fill: var(--down); }

    .candle.synthetic .body {
      fill: var(--surface);
      stroke-dasharray: 2 2;
    }

    .candle.synthetic {
      opacity: 0.7;
    }

    /* Line mode */
    .line {
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
      stroke-linejoin: round;
    }

    .gap-dot {
      position: absolute;
      width: 8px;
      height: 8px;
      margin: -4px 0 0 -4px;
      border: 1.5px dashed currentColor;
      border-radius: 50%;
      background: var(--surface);
      pointer-events: none;
    }

    /* Volume */
    .vol { opacity: 0.32; }
    .vol.up { fill: var(--up); }
    .vol.down { fill: var(--down); }

    /* Last price */
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

    .vol-label {
      transform: none;
      font-size: 10.5px;
      color: var(--ink-400);
    }

    .crosshair {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 1px;
      background: var(--line-strong);
      pointer-events: none;
    }

    .tooltip {
      position: absolute;
      top: 6px;
      margin-left: 12px;
      min-width: 190px;
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

    .tooltip.flip {
      transform: translateX(calc(-100% - 24px));
    }

    .tt-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 6px;
      font-size: 12px;
    }

    .tt-date {
      font-weight: 600;
      color: var(--ink-700);
    }

    .tt-change { font-weight: 700; font-variant-numeric: tabular-nums; }
    .tt-change.up { color: var(--positive); }
    .tt-change.down { color: var(--negative); }

    .tt-grid {
      display: grid;
      grid-template-columns: auto 1fr auto 1fr;
      gap: 3px 8px;
      margin: 0;
      font-size: 12.5px;
      font-variant-numeric: tabular-nums;
    }

    .tt-grid dt {
      color: var(--ink-500);
      font-weight: 600;
    }

    .tt-grid dd {
      margin: 0;
      font-weight: 600;
      text-align: right;
    }

    .tt-foot {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin-top: 6px;
      padding-top: 6px;
      border-top: 1px solid var(--line);
      font-size: 11.5px;
      color: var(--ink-500);
    }

    .tt-foot b {
      color: var(--ink-900);
      font-variant-numeric: tabular-nums;
    }

    .tt-note {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-top: 6px;
      font-size: 11.5px;
      color: var(--warning);
    }

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
export class InstrumentChartComponent {
  private static nextId = 0;

  readonly width = W;
  readonly height = H;
  readonly pane_gap = PANE_GAP;
  readonly gradientId = `chart-fill-${InstrumentChartComponent.nextId++}`;

  readonly candles = signal<Candle[]>([]);
  readonly rangeSignal = signal<ChartRange>('3M');
  readonly decimalsSignal = signal(2);
  readonly modeSignal = signal<ChartMode>('candles');
  readonly hoverIndex = signal<number | null>(null);

  @Input() set series(value: Candle[] | null | undefined) {
    this.candles.set(value ?? []);
    this.hoverIndex.set(null);
  }

  @Input() set range(value: ChartRange) {
    this.rangeSignal.set(value);
  }

  @Input() set decimals(value: number) {
    this.decimalsSignal.set(value);
  }

  @Input() set mode(value: ChartMode) {
    this.modeSignal.set(value);
  }

  readonly isUp = computed(() => {
    const c = this.candles();
    return c.length < 2 || c[c.length - 1].close >= c[0].close;
  });

  /** Volume pane only when the series has any volume (FX has none) */
  readonly hasVolume = computed(() => this.candles().some((c) => c.volume !== null && c.volume > 0));

  private readonly priceBottom = computed(() => (this.hasVolume() ? H * PRICE_SHARE : H - 6));
  readonly volumeTop = computed(() => H * PRICE_SHARE + PANE_GAP);

  /** Price bounds over highs/lows with headroom so wicks never touch the edges */
  private readonly bounds = computed(() => {
    const c = this.candles();
    const min = Math.min(...c.map((k) => k.low));
    const max = Math.max(...c.map((k) => k.high));
    const pad = (max - min || max * 0.01 || 1) * 0.06;
    return { min: min - pad, max: max + pad };
  });

  private readonly maxVolume = computed(() => Math.max(0, ...this.candles().map((c) => c.volume ?? 0)));

  private slot(): number {
    return W / Math.max(this.candles().length, 1);
  }

  private xCenter(index: number): number {
    return (index + 0.5) * this.slot();
  }

  private y(price: number): number {
    const { min, max } = this.bounds();
    return PAD_TOP + (1 - (price - min) / (max - min)) * (this.priceBottom() - PAD_TOP);
  }

  closeY(index: number): number {
    const c = this.candles()[index];
    return c ? this.y(c.close) : 0;
  }

  readonly shapes = computed<CandleShape[]>(() => {
    const slot = this.slot();
    const bodyWidth = Math.max(slot * 0.64, 1.5);
    const maxVolume = this.maxVolume();
    const volumeTop = this.volumeTop();
    return this.candles().map((c, i) => {
      const x = this.xCenter(i);
      const top = this.y(Math.max(c.open, c.close));
      const bottom = this.y(Math.min(c.open, c.close));
      const volumeY = maxVolume > 0 && c.volume ? H - ((c.volume / maxVolume) * (H - volumeTop)) : null;
      return {
        x,
        bodyX: x - bodyWidth / 2,
        bodyWidth,
        bodyY: top,
        bodyHeight: Math.max(bottom - top, 1),
        wickTop: this.y(c.high),
        wickBottom: this.y(c.low),
        up: c.close >= c.open,
        synthetic: c.synthetic,
        volumeY,
      };
    });
  });

  readonly linePath = computed(() =>
    this.candles()
      .map((c, i) => `${i === 0 ? 'M' : 'L'}${this.xCenter(i).toFixed(2)},${this.y(c.close).toFixed(2)}`)
      .join(' ')
  );

  readonly areaPath = computed(() => {
    const line = this.linePath();
    const n = this.candles().length;
    const bottom = this.priceBottom();
    return line ? `${line} L${this.xCenter(n - 1).toFixed(2)},${bottom} L${this.xCenter(0).toFixed(2)},${bottom} Z` : '';
  });

  readonly lastY = computed(() => {
    const c = this.candles();
    return c.length ? this.y(c[c.length - 1].close) : 0;
  });

  readonly lastLabel = computed(() => {
    const c = this.candles();
    return c.length ? this.formatPrice(c[c.length - 1].close) : '';
  });

  readonly maxVolumeLabel = computed(() => compact(this.maxVolume()));

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

  readonly xLabels = computed(() => {
    const c = this.candles();
    if (c.length < 2) {
      return [];
    }
    const count = Math.min(5, c.length);
    const idx = Array.from({ length: count }, (_, i) => Math.round((i * (c.length - 1)) / (count - 1)));
    return idx.map((i) => this.formatAxisDate(c[i].date));
  });

  readonly hover = computed(() => {
    const i = this.hoverIndex();
    const c = this.candles();
    const k = i === null ? undefined : c[i];
    if (i === null || !k) {
      return null;
    }
    const prevClose = i > 0 ? c[i - 1].close : k.open;
    const changePct = prevClose ? ((k.close - prevClose) / prevClose) * 100 : 0;
    return {
      xPct: (this.xCenter(i) / W) * 100,
      date: parseDay(k.date).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }),
      open: this.formatPrice(k.open),
      high: this.formatPrice(k.high),
      low: this.formatPrice(k.low),
      close: this.formatPrice(k.close),
      adjclose: this.formatPrice(k.adjclose),
      volume: k.volume === null ? '—' : k.volume.toLocaleString('en-US'),
      change: `${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%`,
      changeUp: changePct >= 0,
      synthetic: k.synthetic,
    };
  });

  onMove(event: MouseEvent): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const n = this.candles().length;
    if (!rect.width || !n) {
      return;
    }
    const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 0.9999);
    this.hoverIndex.set(Math.floor(ratio * n));
  }

  private formatPrice(value: number): string {
    const d = this.decimalsSignal();
    return value.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }

  private formatAxisDate(date: string): string {
    const day = parseDay(date);
    return day.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
  }
}

function parseDay(date: string): Date {
  return new Date(`${date}T00:00:00Z`);
}

/** 4.6M, 812K */
function compact(value: number): string {
  if (value >= 1e9) return `${(value / 1e9).toFixed(1)}B`;
  if (value >= 1e6) return `${(value / 1e6).toFixed(1)}M`;
  if (value >= 1e3) return `${(value / 1e3).toFixed(0)}K`;
  return String(value);
}
