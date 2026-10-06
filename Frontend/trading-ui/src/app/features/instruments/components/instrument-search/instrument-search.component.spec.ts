import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentSearchComponent } from './instrument-search.component';
import { Instrument } from '../../instrument.models';

describe('InstrumentSearchComponent', () => {
  let fixture: ComponentFixture<InstrumentSearchComponent>;
  let component: InstrumentSearchComponent;
  let picked: Instrument[];
  let dismissed: number;

  const input = (): HTMLInputElement => fixture.nativeElement.querySelector('[data-testid="instrument-search-input"]');
  const results = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="instrument-search-result"]'));
  const type = (value: string) => {
    input().dispatchEvent(new Event('focus'));
    input().value = value;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const key = (k: string) => {
    input().dispatchEvent(new KeyboardEvent('keydown', { key: k }));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InstrumentSearchComponent] }).compileComponents();
    fixture = TestBed.createComponent(InstrumentSearchComponent);
    component = fixture.componentInstance;
    picked = [];
    dismissed = 0;
    component.selected.subscribe((i) => picked.push(i));
    component.dismissed.subscribe(() => dismissed++);
    fixture.detectChanges();
  });

  it('shows no results until something is typed', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="instrument-search-results"]')).toBeNull();
    expect(input().getAttribute('aria-expanded')).toBe('false');
  });

  it('lists matches with name, asset class, currency and a not-tradable tag', () => {
    type('steel');

    expect(results().length).toBe(1);
    expect(results()[0].textContent).toContain('TATASTEEL.BO');
    expect(results()[0].textContent).toContain('Tata Steel Limited');
    expect(results()[0].textContent).toContain('EQUITY · INR');
    expect(results()[0].textContent).toContain('Not tradable');
    expect(input().getAttribute('aria-expanded')).toBe('true');
  });

  it('says when nothing matches', () => {
    type('zzz');

    expect(fixture.nativeElement.textContent).toContain('No instruments match');
  });

  it('moves with the arrow keys (wrapping) and picks with Enter', () => {
    type('a');
    expect(results()[0].classList).toContain('active');

    key('ArrowDown');
    expect(results()[1].classList).toContain('active');
    expect(input().getAttribute('aria-activedescendant')).toBe(results()[1].id);

    key('ArrowUp');
    key('ArrowUp');
    expect(results()[results().length - 1].classList).toContain('active');

    key('ArrowDown');
    key('Enter');
    expect(picked.map((i) => i.symbol)).toEqual(['AAPL']);
    expect(component.query()).toBe('');
  });

  it('picks with a click', () => {
    type('bitcoin');
    results()[0].click();

    expect(picked.map((i) => i.symbol)).toEqual(['X:BTC-USD']);
  });

  it('keeps the query after a pick when clearOnSelect is off', () => {
    fixture.componentRef.setInput('clearOnSelect', false);
    type('acme');
    key('Enter');

    expect(component.query()).toBe('acme');
  });

  it('Escape clears the query first, then dismisses', () => {
    type('inf');
    key('Escape');
    expect(component.query()).toBe('');
    expect(dismissed).toBe(0);

    key('Escape');
    expect(dismissed).toBe(1);
  });

  it('clears with the clear button and hides results on blur', () => {
    type('inf');
    fixture.nativeElement.querySelector('.clear-btn').click();
    fixture.detectChanges();
    expect(component.query()).toBe('');

    type('inf');
    input().dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="instrument-search-results"]')).toBeNull();
  });

  it('can keep the result list open (dialog mode) and show the shortcut hint', () => {
    fixture.componentRef.setInput('alwaysShowResults', true);
    fixture.componentRef.setInput('showShortcut', true);
    fixture.detectChanges();

    expect(results().length).toBe(8);
    expect(fixture.nativeElement.querySelector('.shortcut').textContent).toContain('Ctrl K');

    type('inf');
    expect(fixture.nativeElement.querySelector('.shortcut')).toBeNull();
  });

  it('uses an icon per asset class', () => {
    expect(component.assetIcon({ assetClass: 'FX' } as Instrument)).toBe('fa-money-bill-transfer');
    expect(component.assetIcon({ assetClass: 'CRYPTO' } as Instrument)).toBe('fa-coins');
    expect(component.assetIcon({ assetClass: 'EQUITY' } as Instrument)).toBe('fa-building');
  });
});
