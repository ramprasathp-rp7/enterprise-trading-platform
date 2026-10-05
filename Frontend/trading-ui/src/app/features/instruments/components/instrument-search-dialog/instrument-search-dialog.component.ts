import { Component, EventEmitter, Output } from '@angular/core';
import { Instrument } from '../../instrument.models';
import { InstrumentSearchComponent } from '../instrument-search/instrument-search.component';

/**
 * Modal instrument search, opened from the navbar search icon or Ctrl/⌘+K.
 */
@Component({
  selector: 'app-instrument-search-dialog',
  standalone: true,
  imports: [InstrumentSearchComponent],
  template: `
    <div class="backdrop" (click)="closed.emit()" data-testid="search-dialog-backdrop"></div>
    <div class="dialog" role="dialog" aria-modal="true" aria-label="Search instruments" data-testid="search-dialog">
      <app-instrument-search
        class="inline-results"
        [autofocus]="true"
        [alwaysShowResults]="true"
        placeholder="Search instruments by symbol or name"
        (selected)="selected.emit($event)"
        (dismissed)="closed.emit()"
      />
      <footer class="hints">
        <span><kbd>↑</kbd><kbd>↓</kbd> to move</span>
        <span><kbd>Enter</kbd> to open</span>
        <span><kbd>Esc</kbd> to close</span>
      </footer>
    </div>
  `,
  styles: [`
    :host {
      position: fixed;
      inset: 0;
      z-index: var(--z-modal);
      display: flex;
      justify-content: center;
      align-items: flex-start;
      padding: 12vh 16px 16px;
    }

    .backdrop {
      position: absolute;
      inset: 0;
      background: rgba(11, 19, 36, 0.45);
      backdrop-filter: blur(3px);
      animation: fade-in 140ms ease-out;
    }

    .dialog {
      position: relative;
      width: 100%;
      max-width: 640px;
      padding: 14px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-xl);
      box-shadow: var(--shadow-pop);
      animation: rise-in 180ms var(--ease);
    }

    .hints {
      display: flex;
      gap: 18px;
      margin-top: 10px;
      padding: 10px 6px 2px;
      border-top: 1px solid var(--line);
      font-size: 12px;
      color: var(--ink-500);
    }

    kbd {
      display: inline-block;
      min-width: 20px;
      margin-right: 4px;
      padding: 1px 5px;
      border: 1px solid var(--line-strong);
      border-radius: 5px;
      background: var(--surface-muted);
      font-family: var(--font-sans);
      font-size: 11px;
      text-align: center;
    }

    @keyframes fade-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }
  `],
})
export class InstrumentSearchDialogComponent {
  @Output() selected = new EventEmitter<Instrument>();
  @Output() closed = new EventEmitter<void>();
}
