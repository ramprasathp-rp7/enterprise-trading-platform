import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentPanelComponent } from './instrument-panel.component';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';

describe('InstrumentPanelComponent', () => {
  let fixture: ComponentFixture<InstrumentPanelComponent>;
  let component: InstrumentPanelComponent;

  const show = (symbol: string | null) => {
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
  };
  const text = (sel: string) => fixture.nativeElement.querySelector(sel)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InstrumentPanelComponent] }).compileComponents();
    // Monday 5 Oct 2026, 11:00 in New York
    TestBed.inject(InstrumentCatalogService).now = () => new Date('2026-10-05T15:00:00Z');
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

  describe('INFY.NS (Fauxnance snapshot)', () => {
    beforeEach(() => show('infy.ns'));

    it('labels the data as a snapshot and quotes the last close in INR', () => {
      expect(text('[data-testid="instrument-symbol"]')).toBe('INFY.NS');
      expect(text('[data-testid="data-source"]')).toBe('Fauxnance snapshot');
      expect(text('[data-testid="instrument-last"]')).toBe('1,036.70');
      expect(text('[data-testid="instrument-change"]')).toBe('-4.50 (-0.43%)');
    });

    it('shows the market as closed, the quote as delayed, and the time in IST', () => {
      expect(text('[data-testid="market-state"]')).toBe('Market closed');
      expect(fixture.nativeElement.querySelector('[data-testid="quote-stale"]')).not.toBeNull();
      expect(text('[data-testid="quote-as-of"]')).toBe('as of Jun 29, 3:30 PM IST');
    });

    it('shows bid, ask and spread', () => {
      const book = text('[data-testid="quote-book"]');
      expect(book).toContain('Bid 1,036.49');
      expect(book).toContain('Ask 1,036.91');
      expect(book).toContain('Spread 0.42');
    });

    it('shows previous-session and 52-week stats (no open), with the price placed on the range bar', () => {
      const stats = text('[data-testid="instrument-stats"]');
      expect(stats).not.toContain('Open');
      expect(stats).toContain('Prev close1,041.20');
      expect(stats).toContain('Prev day range1,041.20 – 1,041.20');
      expect(stats).toContain('Prev volume0');
      expect(component.sessionTitle()).toBe('Previous session: Fri, Jun 26, 2026');
      expect(stats).toContain('1,026.00');
      expect(stats).toContain('1,728.00');
      expect(component.yearPosition()).toBeGreaterThanOrEqual(0);
      expect(component.yearPosition()).toBeLessThan(5);
    });

    it('defaults to 3 months of candles and switches range and chart type', () => {
      const threeMonths = component.series().length;
      expect(component.range()).toBe('3M');
      expect(text('[data-testid="range-return"]')).toMatch(/^3M -\d+\.\d{2}%$/);

      fixture.nativeElement.querySelector('[data-testid="range-1Y"]').click();
      fixture.detectChanges();
      expect(component.series().length).toBeGreaterThan(threeMonths);
      expect(fixture.nativeElement.querySelector('[data-testid="range-1Y"]').getAttribute('aria-selected')).toBe('true');

      expect(fixture.nativeElement.querySelector('[data-testid="chart-candles"]')).not.toBeNull();
      fixture.nativeElement.querySelector('[data-testid="mode-line"]').click();
      fixture.detectChanges();
      expect(component.mode()).toBe('line');
      expect(fixture.nativeElement.querySelector('[data-testid="chart-line"]')).not.toBeNull();
    });
  });

  it('labels other instruments as sample data with a live, fresh quote in their exchange time', () => {
    show('aapl');

    expect(text('[data-testid="data-source"]')).toBe('Sample data');
    expect(text('[data-testid="instrument-last"]')).toBe('189.35');
    expect(text('[data-testid="market-state"]')).toBe('Market open');
    expect(fixture.nativeElement.querySelector('[data-testid="quote-stale"]')).toBeNull();
    expect(text('[data-testid="quote-as-of"]')).toBe('as of Oct 5, 11:00 AM ET');
    expect(fixture.nativeElement.querySelector('[data-testid="not-tradable-note"]')).toBeNull();
  });

  it('warns when the instrument is not tradable', () => {
    show('TATASTEEL.BO');

    const note = fixture.nativeElement.querySelector('[data-testid="not-tradable-note"]');
    expect(note.textContent.trim()).toBe('Not tradable');
    expect(note.getAttribute('title')).toBe('TATASTEEL.BO is currently not tradable. Orders for it will be rejected.');
  });

  it('formats FX prices to 4 decimals in UTC, with asset-specific icons', () => {
    show('FX:EURUSD');

    expect(component.decimals()).toBe(4);
    expect(text('[data-testid="instrument-last"]')).toBe('1.0842');
    expect(text('[data-testid="quote-as-of"]')).toBe('as of Oct 5, 3:00 PM UTC');
    expect(text('[data-testid="instrument-stats"]')).toContain('Prev volume—');
    expect(component.assetIcon()).toBe('fa-money-bill-transfer');

    show('X:BTC-USD');
    expect(component.assetIcon()).toBe('fa-coins');
    expect(text('[data-testid="instrument-last"]')).toBe('64,250.00');

    show('ACME');
    expect(component.assetIcon()).toBe('fa-building');
  });

  it('maps every market state to a label', () => {
    expect(['open', 'closed', 'pre', 'post', 'unknown'].map((s) => component.marketStateLabel(s as any))).toEqual([
      'Market open', 'Market closed', 'Pre-market', 'After hours', 'Status unknown',
    ]);
  });
});
