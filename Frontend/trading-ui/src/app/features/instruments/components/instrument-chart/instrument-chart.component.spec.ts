import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentChartComponent } from './instrument-chart.component';
import { Candle, ChartMode, ChartRange } from '../../instrument.models';

describe('InstrumentChartComponent', () => {
  let fixture: ComponentFixture<InstrumentChartComponent>;
  let component: InstrumentChartComponent;

  /** Candles closing at the given prices, one per weekday-ish date */
  const candles = (closes: number[], extra: Partial<Candle>[] = []): Candle[] =>
    closes.map((close, i) => ({
      date: new Date(Date.UTC(2026, 5, 1 + i)).toISOString().slice(0, 10),
      open: i === 0 ? close : closes[i - 1],
      high: Math.max(close, i === 0 ? close : closes[i - 1]) + 1,
      low: Math.min(close, i === 0 ? close : closes[i - 1]) - 1,
      close,
      adjclose: close,
      volume: 1000 * (i + 1),
      synthetic: false,
      ...(extra[i] ?? {}),
    }));

  const render = (series: Candle[], range: ChartRange = '3M', decimals = 2, mode: ChartMode = 'candles') => {
    fixture.componentRef.setInput('series', series);
    fixture.componentRef.setInput('range', range);
    fixture.componentRef.setInput('decimals', decimals);
    fixture.componentRef.setInput('mode', mode);
    fixture.detectChanges();
  };
  const all = (sel: string): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll(sel));

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InstrumentChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(InstrumentChartComponent);
    component = fixture.componentInstance;
  });

  it('shows an empty message without enough data', () => {
    render([]);
    expect(fixture.nativeElement.textContent).toContain('No price history to display');

    render(candles([10]));
    expect(fixture.nativeElement.querySelector('[data-testid="instrument-chart"]')).toBeNull();
  });

  it('draws one candle per bar, coloured by direction, with a volume bar each', () => {
    render(candles([10, 12, 11, 13]));

    const drawn = all('[data-testid="chart-candles"] .candle');
    expect(drawn.length).toBe(4);
    expect(drawn.map((g) => g.classList.contains('up'))).toEqual([true, true, false, true]);
    expect(all('rect.vol').length).toBe(4);
    expect(component.hasVolume()).toBe(true);
  });

  it('keeps wicks spanning high to low and bodies spanning open to close', () => {
    render(candles([10, 14]));
    const [, second] = component.shapes();

    expect(second.wickTop).toBeLessThan(second.bodyY);
    expect(second.wickBottom).toBeGreaterThan(second.bodyY + second.bodyHeight);
    expect(second.bodyHeight).toBeGreaterThan(1);
  });

  it('draws synthetic bars hollow, without volume, and flags them in the tooltip', () => {
    render(candles([10, 11, 12], [{}, { synthetic: true, volume: null }]));

    expect(all('.candle.synthetic').length).toBe(1);
    expect(all('rect.vol').length).toBe(2);

    component.hoverIndex.set(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="tooltip-synthetic"]')).not.toBeNull();
    expect(component.hover()?.volume).toBe('—');
  });

  it('switches to a close-price line with an area, marking synthetic closes', () => {
    render(candles([10, 12, 11, 13], [{}, { synthetic: true, volume: null }]), '3M', 2, 'line');

    const d = fixture.nativeElement.querySelector('[data-testid="chart-line"]').getAttribute('d');
    expect(d.match(/[ML]/g).length).toBe(4);
    expect(component.areaPath().endsWith('Z')).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="chart-candles"]')).toBeNull();
    expect(all('.gap-dot').length).toBe(1);
  });

  it('drops the volume pane when the series has no volume (FX)', () => {
    render(candles([1.08, 1.09, 1.085]).map((c) => ({ ...c, volume: null })), '1M', 4);

    expect(component.hasVolume()).toBe(false);
    expect(all('rect.vol').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.vol-label')).toBeNull();
  });

  it('is green when the period is up and red when it is down', () => {
    render(candles([10, 11, 12]));
    expect(component.isUp()).toBe(true);
    expect(fixture.nativeElement.querySelector('.chart').classList).not.toContain('down');

    render(candles([12, 11, 10]));
    expect(component.isUp()).toBe(false);
    expect(fixture.nativeElement.querySelector('.chart').classList).toContain('down');
  });

  it('tags the last close on the price axis and hides axis labels it would cover', () => {
    render(candles([1.0812, 1.0855, 1.0842]), '1M', 4);

    expect(fixture.nativeElement.querySelector('[data-testid="chart-last-tag"]').textContent.trim()).toBe('1.0842');
    const labels = all('.y-label:not(.vol-label)').map((e) => e.textContent!.trim());
    expect(labels.length).toBe(component.axisLabels().length);
    expect(labels.length).toBeLessThanOrEqual(4);
    expect(labels.every((l) => /^\d\.\d{4}$/.test(l))).toBe(true);
  });

  it('labels five evenly spaced days, month and day, whatever the range', () => {
    const series = candles(Array.from({ length: 30 }, (_, i) => 100 + i));

    render(series, '3M');
    expect(component.xLabels()).toEqual(['Jun 1', 'Jun 8', 'Jun 16', 'Jun 23', 'Jun 30']);

    render(series, '1M');
    expect(component.xLabels()[0]).toBe('Jun 1');
  });

  it('shows the OHLCV bar under the mouse and hides it on leave', () => {
    render(candles([10, 20, 30, 40, 50]));
    const canvas: HTMLElement = fixture.nativeElement.querySelector('.canvas');
    canvas.getBoundingClientRect = () => ({ left: 100, width: 500, top: 0, height: 300, right: 600, bottom: 300, x: 100, y: 0, toJSON: () => ({}) });

    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX: 350 }));
    fixture.detectChanges();
    expect(component.hoverIndex()).toBe(2);
    const tooltip = fixture.nativeElement.querySelector('[data-testid="chart-tooltip"]').textContent;
    expect(tooltip).toContain('Wed, Jun 3, 2026');
    expect(tooltip).toContain('30.00');
    expect(tooltip).toContain('+50.00%');
    expect(tooltip).toContain('3,000');

    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX: 9999 }));
    expect(component.hoverIndex()).toBe(4);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tooltip').classList).toContain('flip');

    canvas.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="chart-tooltip"]')).toBeNull();
  });

  it('ignores mouse movement before layout (zero width)', () => {
    render(candles([1, 2]));
    const canvas: HTMLElement = fixture.nativeElement.querySelector('.canvas');
    canvas.getBoundingClientRect = () => ({ left: 0, width: 0 } as DOMRect);

    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX: 10 }));

    expect(component.hoverIndex()).toBeNull();
  });

  it('labels the tallest volume compactly', () => {
    render(candles([1, 2]).map((c, i) => ({ ...c, volume: i ? 4_630_590 : 812_000 })));
    expect(component.maxVolumeLabel()).toBe('4.6M');

    render(candles([1, 2]).map((c, i) => ({ ...c, volume: i ? 812_000 : 10 })));
    expect(component.maxVolumeLabel()).toBe('812K');
  });
});
