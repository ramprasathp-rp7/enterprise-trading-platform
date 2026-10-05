import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentPanelComponent } from './instrument-panel.component';

describe('InstrumentPanelComponent', () => {
  let fixture: ComponentFixture<InstrumentPanelComponent>;
  let component: InstrumentPanelComponent;

  const show = (symbol: string | null) => {
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
  };
  const text = (sel: string) => fixture.nativeElement.querySelector(sel)?.textContent?.trim() ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InstrumentPanelComponent] }).compileComponents();
    fixture = TestBed.createComponent(InstrumentPanelComponent);
    component = fixture.componentInstance;
  });

  it('prompts to search when no instrument is chosen', () => {
    show(null);

    expect(text('[data-testid="instrument-panel-empty"]')).toContain('Search for an instrument');
  });

  it('says when the symbol is not in the catalogue', () => {
    show('nope');

    expect(text('[data-testid="instrument-panel-empty"]')).toContain('No details for “NOPE”');
  });

  it('shows identity, sample-data label, price, change and stats for a known instrument', () => {
    show('aapl');

    expect(text('[data-testid="instrument-symbol"]')).toBe('AAPL');
    expect(fixture.nativeElement.textContent).toContain('Apple Inc.');
    expect(fixture.nativeElement.textContent).toContain('Sample data');
    expect(text('[data-testid="instrument-last"]')).toBe('189.35');
    expect(text('[data-testid="instrument-change"]')).toMatch(/[+-]?\d+\.\d{2} \([+-]?\d+\.\d{2}%\)/);
    expect(fixture.nativeElement.querySelectorAll('.stats > div').length).toBe(6);
    expect(fixture.nativeElement.querySelector('[data-testid="instrument-chart"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="not-tradable-note"]')).toBeNull();
  });

  it('warns when the instrument is not tradable', () => {
    show('TATASTEEL.BO');

    expect(text('[data-testid="not-tradable-note"]')).toContain('not tradable');
  });

  it('switches the chart range from the tabs', () => {
    show('AAPL');
    const dayPoints = component.series().length;

    fixture.nativeElement.querySelector('[data-testid="range-1Y"]').click();
    fixture.detectChanges();

    expect(component.range()).toBe('1Y');
    expect(component.series().length).not.toBe(dayPoints);
    expect(fixture.nativeElement.querySelector('[data-testid="range-1Y"]').getAttribute('aria-selected')).toBe('true');
  });

  it('formats FX prices to 4 decimals with an asset-specific icon', () => {
    show('FX:EURUSD');

    expect(component.decimals()).toBe(4);
    expect(text('[data-testid="instrument-last"]')).toBe('1.0842');
    expect(component.assetIcon()).toBe('fa-money-bill-transfer');

    show('X:BTC-USD');
    expect(component.assetIcon()).toBe('fa-coins');
    expect(text('[data-testid="instrument-last"]')).toBe('64,250.00');

    show('ACME');
    expect(component.assetIcon()).toBe('fa-building');
  });
});
