import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NetWorthChartComponent, NetWorthPoint, withLiveValue } from './net-worth-chart.component';

const DAY_MS = 86_400_000;

/** UTC midnight `daysAgo` days before today */
function day(daysAgo: number): Date {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()) - daysAgo * DAY_MS);
}

/** One snapshot a day from `days` days ago up to yesterday, rising by 100 a day from 10,000 */
function dailyHistory(days: number): NetWorthPoint[] {
  return Array.from({ length: days }, (_, i) => ({ date: day(days - i), value: 10_000 + i * 100 }));
}

describe('withLiveValue', () => {
  const today = new Date('2026-10-06T12:00:00Z');
  const history: NetWorthPoint[] = [
    { date: new Date('2026-10-04T00:00:00Z'), value: 100 },
    { date: new Date('2026-10-05T00:00:00Z'), value: 110 },
  ];

  it("appends today's live value after the last snapshot", () => {
    expect(withLiveValue(history, 125, today)).toEqual([
      ...history,
      { date: new Date('2026-10-06T00:00:00Z'), value: 125 },
    ]);
  });

  it("replaces today's snapshot with the live value", () => {
    const withToday = [...history, { date: new Date('2026-10-06T00:00:00Z'), value: 120 }];

    expect(withLiveValue(withToday, 125, today).at(-1)).toEqual({ date: new Date('2026-10-06T00:00:00Z'), value: 125 });
    expect(withLiveValue(withToday, 125, today)).toHaveLength(3);
  });

  it('returns the history unchanged without a live value', () => {
    expect(withLiveValue(history, null, today)).toEqual(history);
  });
});

describe('NetWorthChartComponent', () => {
  let fixture: ComponentFixture<NetWorthChartComponent>;
  let component: NetWorthChartComponent;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [NetWorthChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(NetWorthChartComponent);
    component = fixture.componentInstance;
    el = fixture.nativeElement;
  });

  function render(inputs: { history?: NetWorthPoint[] | null; failed?: boolean; currentValue?: number | null; currency?: string }): void {
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
  }

  it('plots the snapshot history ending at the live portfolio value, in its currency', () => {
    render({ history: dailyHistory(10), currentValue: 127_345.67, currency: 'USD' });

    expect(component.hasChart()).toBe(true);
    expect(component.points()).toHaveLength(11);
    expect(component.points().at(-1)).toEqual({ date: day(0), value: 127_345.67 });
    expect(el.querySelector('canvas')).not.toBeNull();
    expect(el.querySelector('[data-testid="net-worth-value"]')!.textContent).toContain('$127,345.67');
    expect(el.textContent).not.toContain('Sample');
  });

  it('ends at the last snapshot while the live value is loading', () => {
    render({ history: dailyHistory(10) });

    expect(component.latest()).toBe(10_900);
  });

  it('switches range by date and reports the change over it', () => {
    render({ history: dailyHistory(120), currentValue: 40_000 });
    expect(component.range()).toBe('3M');
    // 89 snapshot days plus today
    expect(component.points()).toHaveLength(90);

    const buttons: HTMLButtonElement[] = Array.from(el.querySelectorAll('.nw-range'));
    expect(buttons.map((b) => b.textContent!.trim())).toEqual(['1W', '1M', '3M']);
    buttons[0].click();
    fixture.detectChanges();

    expect(component.range()).toBe('1W');
    expect(component.points()).toHaveLength(7);
    const first = component.points()[0].value;
    expect(component.change()).toBeCloseTo(40_000 - first, 2);
    expect(component.changePercent()).toBeCloseTo(((40_000 - first) / first) * 100, 6);
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('.nw-period')!.textContent).toContain('past week');
  });

  it('keeps gaps in the history instead of padding them', () => {
    render({ history: [{ date: day(20), value: 900 }, { date: day(3), value: 1_000 }], currentValue: 1_100 });

    expect(component.points().map((p) => p.value)).toEqual([900, 1_000, 1_100]);
    expect(component.change()).toBe(200);
  });

  it('shows a loading message until the history arrives', () => {
    render({});

    expect(component.hasChart()).toBe(false);
    expect(el.querySelector('canvas')).toBeNull();
    expect(el.querySelector('[data-testid="net-worth-state"]')!.textContent).toContain('Loading');
  });

  it('says the history is unavailable when it failed to load', () => {
    render({ history: null, failed: true, currentValue: 5_000 });

    expect(el.querySelector('canvas')).toBeNull();
    expect(el.querySelector('[data-testid="net-worth-state"]')!.textContent).toContain('unavailable');
  });

  it("explains that a new account's history builds up daily, showing today's value", () => {
    render({ history: [], currentValue: 5_000, currency: 'USD' });

    expect(el.querySelector('canvas')).toBeNull();
    expect(el.querySelector('[data-testid="net-worth-state"]')!.textContent).toContain('end of each day');
    expect(el.querySelector('[data-testid="net-worth-value"]')!.textContent).toContain('$5,000.00');
  });

  it('suggests a longer range when the selected one has too few points', () => {
    render({ history: [{ date: day(40), value: 900 }], currentValue: 1_000 });

    component.range.set('1W');
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="net-worth-state"]')!.textContent).toContain('longer range');

    component.range.set('3M');
    fixture.detectChanges();
    expect(component.hasChart()).toBe(true);
  });

  it('labels the y axis compactly with the currency symbol', () => {
    render({ currency: 'INR' });

    expect(component.formatCompact(125_000)).toBe('₹125K');
    expect(component.formatMoney(1234.5)).toBe('₹1,234.50');
  });
});
