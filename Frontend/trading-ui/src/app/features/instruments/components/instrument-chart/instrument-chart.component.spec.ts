import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentChartComponent } from './instrument-chart.component';
import { ChartRange, PricePoint } from '../../instrument.models';

describe('InstrumentChartComponent', () => {
  let fixture: ComponentFixture<InstrumentChartComponent>;
  let component: InstrumentChartComponent;

  const series = (prices: number[], stepMs = 3_600_000): PricePoint[] =>
    prices.map((price, i) => ({ time: new Date(Date.UTC(2026, 9, 5, 9) + i * stepMs), price }));

  const render = (points: PricePoint[], range: ChartRange = '1D', decimals = 2) => {
    fixture.componentRef.setInput('series', points);
    fixture.componentRef.setInput('range', range);
    fixture.componentRef.setInput('decimals', decimals);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InstrumentChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(InstrumentChartComponent);
    component = fixture.componentInstance;
  });

  it('shows an empty message without enough data', () => {
    render([]);
    expect(fixture.nativeElement.textContent).toContain('No price history to display');

    render(series([10]));
    expect(fixture.nativeElement.querySelector('[data-testid="instrument-chart"]')).toBeNull();
  });

  it('draws a line through every point and a closed area beneath it', () => {
    render(series([10, 12, 11, 13]));

    const d = fixture.nativeElement.querySelector('[data-testid="chart-line"]').getAttribute('d');
    expect(d.match(/[ML]/g).length).toBe(4);
    expect(d.startsWith('M0.00,')).toBe(true);
    expect(component.areaPath().endsWith('Z')).toBe(true);
  });

  it('is green when the period is up and red when it is down', () => {
    render(series([10, 11, 12]));
    expect(component.isUp()).toBe(true);
    expect(fixture.nativeElement.querySelector('.chart').classList).not.toContain('down');

    render(series([12, 11, 10]));
    expect(component.isUp()).toBe(false);
    expect(fixture.nativeElement.querySelector('.chart').classList).toContain('down');
  });

  it('labels four price gridlines within the data range, using the decimals given', () => {
    render(series([1.0812, 1.0855, 1.0842]), '1D', 4);

    const labels: string[] = Array.from(fixture.nativeElement.querySelectorAll('.y-label')).map((e: any) => e.textContent.trim());
    expect(labels.length).toBe(4);
    expect(labels.every((l) => /^1\.\d{4}$/.test(l))).toBe(true);
  });

  it('formats time labels for the selected range', () => {
    const points = series([1, 2, 3, 4, 5, 6, 7], 30 * 86_400_000);

    render(points, '1Y');
    expect(component.xLabels().length).toBe(4);
    expect(component.xLabels()[0]).toMatch(/[A-Z][a-z]{2} \d{2}/);

    render(points, '1W');
    expect(component.xLabels()[0]).toMatch(/[A-Z][a-z]{2}/);

    render(points, '1M');
    expect(component.xLabels()[0]).toMatch(/[A-Z][a-z]{2} \d+/);

    render(series([1, 2, 3]), '1D');
    expect(component.xLabels()[0]).toMatch(/\d{2}:\d{2}/);
  });

  it('shows a crosshair tooltip for the point under the mouse and hides it on leave', () => {
    render(series([10, 20, 30, 40, 50]));
    const canvas: HTMLElement = fixture.nativeElement.querySelector('.canvas');
    canvas.getBoundingClientRect = () => ({ left: 100, width: 400, top: 0, height: 300, right: 500, bottom: 300, x: 100, y: 0, toJSON: () => ({}) });

    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX: 300 }));
    fixture.detectChanges();
    expect(component.hoverIndex()).toBe(2);
    expect(fixture.nativeElement.querySelector('[data-testid="chart-tooltip"]').textContent).toContain('30.00');

    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX: 9999 }));
    expect(component.hoverIndex()).toBe(4);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tooltip').classList).toContain('flip');

    canvas.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="chart-tooltip"]')).toBeNull();
  });

  it('ignores mouse movement before layout (zero width)', () => {
    render(series([1, 2]));
    const canvas: HTMLElement = fixture.nativeElement.querySelector('.canvas');
    canvas.getBoundingClientRect = () => ({ left: 0, width: 0 } as DOMRect);

    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX: 10 }));

    expect(component.hoverIndex()).toBeNull();
  });

  it('uses date-only tooltips for long ranges', () => {
    render(series([1, 2, 3], 86_400_000), '3M');
    component.hoverIndex.set(1);

    expect(component.hover()?.time).toMatch(/\d{4}/);
  });
});
