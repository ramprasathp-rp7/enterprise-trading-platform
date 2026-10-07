import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NetWorthChartComponent, sampleNetWorthHistory } from './net-worth-chart.component';

describe('sampleNetWorthHistory', () => {
  const today = new Date('2026-10-06T12:00:00Z');

  it('builds one point per day ending today at the given value', () => {
    const history = sampleNetWorthHistory(127_345.67, today);

    expect(history).toHaveLength(365);
    expect(history[history.length - 1]).toEqual({ date: new Date('2026-10-06T00:00:00Z'), value: 127_345.67 });
    expect(history[0].date).toEqual(new Date('2025-10-07T00:00:00Z'));
    expect(history.every((p) => p.value > 0)).toBe(true);
  });

  it('is deterministic, so the chart is the same on every visit', () => {
    expect(sampleNetWorthHistory(50_000, today)).toEqual(sampleNetWorthHistory(50_000, today));
  });
});

describe('NetWorthChartComponent', () => {
  let fixture: ComponentFixture<NetWorthChartComponent>;
  let component: NetWorthChartComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [NetWorthChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(NetWorthChartComponent);
    component = fixture.componentInstance;
  });

  it('ends the sample history at the real portfolio value, in its currency', () => {
    fixture.componentRef.setInput('currentValue', 127_345.67);
    fixture.componentRef.setInput('currency', 'USD');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="net-worth-value"]')!.textContent).toContain('$127,345.67');
    expect(el.querySelector('.sample-tag')!.textContent).toContain('Sample');
  });

  it('shows a sample value while the portfolio value is loading', () => {
    fixture.detectChanges();

    expect(component.latest()).toBe(125_000);
  });

  it('switches range and reports the change over it', () => {
    fixture.componentRef.setInput('currentValue', 100_000);
    fixture.detectChanges();
    expect(component.points()).toHaveLength(90);

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.nw-range'));
    expect(buttons.map((b) => b.textContent!.trim())).toEqual(['1W', '1M', '3M', '6M', '1Y']);
    buttons[0].click();
    fixture.detectChanges();

    expect(component.range()).toBe('1W');
    expect(component.points()).toHaveLength(7);
    const first = component.points()[0].value;
    expect(component.change()).toBeCloseTo(100_000 - first, 2);
    expect(component.changePercent()).toBeCloseTo(((100_000 - first) / first) * 100, 6);
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.querySelector('.nw-period').textContent).toContain('past week');
  });

  it('labels the y axis compactly with the currency symbol', () => {
    fixture.componentRef.setInput('currency', 'INR');
    fixture.detectChanges();

    expect(component.formatCompact(125_000)).toBe('₹125K');
    expect(component.formatMoney(1234.5)).toBe('₹1,234.50');
  });
});
