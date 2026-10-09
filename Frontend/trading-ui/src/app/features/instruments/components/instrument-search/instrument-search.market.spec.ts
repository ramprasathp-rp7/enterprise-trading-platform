import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { InstrumentSearchComponent } from './instrument-search.component';
import { Instrument } from '../../instrument.models';
import { AccountSettingsStore, Market } from '../../../../core/settings/account-settings.store';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';

describe('InstrumentSearchComponent: market preference and symbol discovery', () => {
  let fixture: ComponentFixture<InstrumentSearchComponent>;
  let component: InstrumentSearchComponent;
  let market: ReturnType<typeof signal<Market>>;
  let picked: Instrument[];
  let discover: ReturnType<typeof vi.fn>;

  const el = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const symbols = () =>
    Array.from(fixture.nativeElement.querySelectorAll('[data-testid="instrument-search-result"] .result-symbol')).map((e) =>
      (e as HTMLElement).textContent?.trim(),
    );
  const type = (value: string) => {
    const input = el('instrument-search-input') as HTMLInputElement;
    input.dispatchEvent(new Event('focus'));
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    market = signal<Market>('USA');
    await TestBed.configureTestingModule({
      imports: [InstrumentSearchComponent],
      providers: [{ provide: AccountSettingsStore, useValue: { market } }],
    }).compileComponents();
    fixture = TestBed.createComponent(InstrumentSearchComponent);
    component = fixture.componentInstance;
    picked = [];
    component.selected.subscribe((i) => picked.push(i));
    discover = vi.spyOn(TestBed.inject(InstrumentCatalogService), 'discover') as unknown as ReturnType<typeof vi.fn>;
    fixture.detectChanges();
  });

  it('limits results to the preferred market and says so', () => {
    type('in');

    expect(symbols()).not.toContain('INFY.NS');
    expect(el('instrument-search-market')?.textContent).toContain('US markets');
  });

  it('can show every market for this search', () => {
    type('infosys');
    expect(symbols()).toEqual([]);

    (el('instrument-search-market')?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(symbols()).toEqual(['INFY.NS']);
    expect(el('instrument-search-market')).toBeNull();
  });

  it('shows every market with the ALL preference, and an explicit market input wins', () => {
    market.set('ALL');
    type('infosys');
    expect(symbols()).toEqual(['INFY.NS']);

    fixture.componentRef.setInput('market', 'NSE');
    type('a');
    expect(symbols().every((s) => s?.endsWith('.NS'))).toBe(true);
  });

  it('offers to look up a symbol that is not listed, and picks it when found', () => {
    discover.mockReturnValue(
      of({ instrument: { id: 26, symbol: 'SHOP', name: 'Shopify Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true, exchange: 'NYSE' }, created: true }),
    );
    type('shop');

    expect(el('instrument-discover')?.textContent).toContain('Look up “SHOP”');
    (el('instrument-discover') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(discover).toHaveBeenCalledWith('SHOP');
    expect(picked.map((i) => i.symbol)).toEqual(['SHOP']);
  });

  it('shows why a lookup failed', () => {
    discover.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'INS-404', message: 'No market data was found for ZZZZ.' } })),
    );
    type('zzzz');
    (el('instrument-discover') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el('instrument-discover-error')?.textContent).toContain('No market data was found for ZZZZ.');
    expect(picked).toEqual([]);
  });

  it('does not offer a lookup for a listed symbol or for text that cannot be a symbol', () => {
    market.set('ALL');
    type('aapl');
    expect(el('instrument-discover')).toBeNull();

    type('apple inc');
    expect(el('instrument-discover')).toBeNull();
  });
});
