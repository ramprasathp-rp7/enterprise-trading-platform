import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentSearchDialogComponent } from './instrument-search-dialog.component';
import { Instrument } from '../../instrument.models';

describe('InstrumentSearchDialogComponent', () => {
  let fixture: ComponentFixture<InstrumentSearchDialogComponent>;
  let picked: Instrument[];
  let closed: number;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InstrumentSearchDialogComponent] }).compileComponents();
    fixture = TestBed.createComponent(InstrumentSearchDialogComponent);
    picked = [];
    closed = 0;
    fixture.componentInstance.selected.subscribe((i) => picked.push(i));
    fixture.componentInstance.closed.subscribe(() => closed++);
    fixture.detectChanges();
  });

  it('is a modal dialog listing instruments straight away', () => {
    const dialog = fixture.nativeElement.querySelector('[data-testid="search-dialog"]');
    expect(dialog.getAttribute('role')).toBe('dialog');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="instrument-search-result"]').length).toBe(8);
  });

  it('passes on the picked instrument', () => {
    fixture.nativeElement.querySelectorAll('[data-testid="instrument-search-result"]')[2].click();

    expect(picked.map((i) => i.symbol)).toEqual(['INFY.NS']);
  });

  it('closes on a backdrop click or Escape with an empty query', () => {
    fixture.nativeElement.querySelector('[data-testid="search-dialog-backdrop"]').click();
    expect(closed).toBe(1);

    const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="instrument-search-input"]');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).toBe(2);
  });
});
