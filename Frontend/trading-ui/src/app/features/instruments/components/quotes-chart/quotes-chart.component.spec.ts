import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuotesChartComponent } from './quotes-chart.component';
import { QuotePoint } from '../../instrument.models';

const points: QuotePoint[] = [
  { time: '2026-10-09T09:00:00Z', price: 100, bid: 99.9, ask: 100.1 },
  { time: '2026-10-09T09:05:00Z', price: 102, bid: 101.9, ask: 102.1 },
  { time: '2026-10-09T09:10:00Z', price: 98, bid: 97.9, ask: 98.1 },
];

describe('QuotesChartComponent', () => {
  let fixture: ComponentFixture<QuotesChartComponent>;
  let component: QuotesChartComponent;
  const q = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [QuotesChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(QuotesChartComponent);
    component = fixture.componentInstance;
    component.decimals = 2;
    component.timeZone = 'UTC';
    component.zoneLabel = 'UTC';
  });

  const move = (clientX: number, width = 200) => {
    const canvas = fixture.nativeElement.querySelector('.canvas') as HTMLElement;
    canvas.getBoundingClientRect = () => ({ left: 0, width, top: 0, height: 100, right: width, bottom: 100, x: 0, y: 0, toJSON: () => ({}) });
    canvas.dispatchEvent(new MouseEvent('mousemove', { clientX }));
    fixture.detectChanges();
  };

  it('says when nothing is cached yet, or only one quote', () => {
    component.quotes = [];
    fixture.detectChanges();
    expect(q('quotes-chart-empty')?.textContent).toContain('No quotes cached today yet');

    component.quotes = [points[0]];
    fixture.detectChanges();
    expect(q('quotes-chart-empty')?.textContent).toContain('One quote cached so far today');
    expect(component.xLabels()).toEqual([]);
  });

  it('draws the line with a baseline at the previous close and time labels', () => {
    component.quotes = points;
    component.previousClose = 99;
    fixture.detectChanges();

    expect(q('quotes-line')?.getAttribute('d')).toContain('M');
    expect(q('quotes-baseline')).not.toBeNull();
    expect(component.xLabels()).toHaveLength(5);
    expect(q('quotes-last-tag')?.textContent).toContain('98.00');
  });

  it('shows the nearest quote under the pointer with its change from the previous close', () => {
    component.quotes = points;
    component.previousClose = 100;
    fixture.detectChanges();

    move(105);
    expect(component.hoverIndex()).toBe(1);
    const tip = q('quotes-tooltip')!;
    expect(tip.textContent).toContain('102.00');
    expect(tip.textContent).toContain('+2.00%');
    expect(tip.textContent).toContain('101.90');

    move(500); // past the right edge: the last quote, which is down
    expect(component.hoverIndex()).toBe(2);
    expect(component.hover()?.change).toBe('-2.00%');
    expect(component.hover()?.changeUp).toBe(false);
    expect(q('quotes-tooltip')!.classList.contains('flip')).toBe(true);

    (fixture.nativeElement.querySelector('.canvas') as HTMLElement).dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(q('quotes-tooltip')).toBeNull();
  });

  it('measures change from the first quote when there is no previous close, and ignores a zero-width canvas', () => {
    component.quotes = points;
    fixture.detectChanges();

    move(0);
    expect(component.hover()?.change).toBe('');

    component.hoverIndex.set(null);
    move(50, 0);
    expect(component.hoverIndex()).toBeNull();
  });
});
