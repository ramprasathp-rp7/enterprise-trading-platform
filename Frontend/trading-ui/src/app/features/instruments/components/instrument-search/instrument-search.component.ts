import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Output,
  ViewChild,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Instrument } from '../../instrument.models';
import { InstrumentCatalogService } from '../../services/instrument-catalog.service';

let nextId = 0;

/**
 * Instrument search box (accessible combobox).
 * Type a symbol or name; ↑/↓ to move, Enter to pick, Esc to close.
 */
@Component({
  selector: 'app-instrument-search',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="instrument-search" [class.open]="showResults()">
      <div class="search-field">
        <i class="fas fa-magnifying-glass search-icon" aria-hidden="true"></i>
        <input
          #input
          type="text"
          class="search-input-field"
          role="combobox"
          aria-autocomplete="list"
          [attr.aria-expanded]="showResults()"
          [attr.aria-controls]="listId"
          [attr.aria-activedescendant]="showResults() && results().length ? optionId(activeIndex()) : null"
          [attr.aria-label]="placeholder()"
          [placeholder]="placeholder()"
          [value]="query()"
          (input)="onInput($any($event.target).value)"
          (focus)="focused.set(true)"
          (blur)="onBlur()"
          (keydown)="onKeydown($event)"
          autocomplete="off"
          spellcheck="false"
          data-testid="instrument-search-input"
        />
        @if (query()) {
          <button type="button" class="clear-btn" aria-label="Clear search" (mousedown)="$event.preventDefault()" (click)="clear()">
            <i class="fas fa-xmark"></i>
          </button>
        } @else if (showShortcut()) {
          <kbd class="shortcut">Ctrl K</kbd>
        }
      </div>

      @if (showResults()) {
        <ul class="results" role="listbox" [id]="listId" data-testid="instrument-search-results">
          @for (instrument of results(); track instrument.symbol; let i = $index) {
            <li
              role="option"
              class="result"
              [id]="optionId(i)"
              [class.active]="i === activeIndex()"
              [attr.aria-selected]="i === activeIndex()"
              (mousedown)="$event.preventDefault()"
              (mouseenter)="activeIndex.set(i)"
              (click)="choose(instrument)"
              data-testid="instrument-search-result"
            >
              <span class="result-icon" [attr.data-asset]="instrument.assetClass">
                <i class="fas" [ngClass]="assetIcon(instrument)"></i>
              </span>
              <span class="result-text">
                <span class="result-symbol">{{ instrument.symbol }}</span>
                <span class="result-name">{{ instrument.name }}</span>
              </span>
              <span class="result-tags">
                <span class="tag">{{ instrument.assetClass }} · {{ instrument.currency }}</span>
                @if (!instrument.tradable) {
                  <span class="tag tag-warn"><i class="fas fa-ban" aria-hidden="true"></i> Not tradable</span>
                }
              </span>
            </li>
          } @empty {
            <li class="no-results" role="presentation">No instruments match “{{ query() }}”</li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }

    .instrument-search {
      position: relative;
    }

    .search-field {
      position: relative;
      display: flex;
      align-items: center;
    }

    .search-icon {
      position: absolute;
      left: 16px;
      color: var(--ink-400);
      font-size: 14px;
      pointer-events: none;
    }

    .search-input-field {
      width: 100%;
      height: 48px;
      padding: 0 84px 0 44px;
      border: 1px solid var(--line-strong);
      border-radius: var(--r-md);
      background: var(--surface);
      color: var(--ink-900);
      font-size: 15px;
      transition: border-color var(--t-fast), box-shadow var(--t-fast);
    }

    .search-input-field::placeholder {
      color: var(--ink-400);
    }

    .search-input-field:focus {
      outline: none;
      border-color: var(--brand);
      box-shadow: 0 0 0 4px var(--brand-ring);
    }

    .clear-btn {
      position: absolute;
      right: 10px;
      width: 30px;
      height: 30px;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: var(--ink-500);
      cursor: pointer;
    }

    .clear-btn:hover {
      background: var(--surface-sunken);
      color: var(--ink-900);
    }

    .shortcut {
      position: absolute;
      right: 12px;
      padding: 3px 7px;
      border: 1px solid var(--line-strong);
      border-radius: 6px;
      background: var(--surface-muted);
      color: var(--ink-500);
      font-family: var(--font-sans);
      font-size: 11px;
      font-weight: 600;
      pointer-events: none;
    }

    .results {
      position: absolute;
      top: calc(100% + 8px);
      left: 0;
      right: 0;
      z-index: 30;
      max-height: 360px;
      overflow-y: auto;
      margin: 0;
      padding: 6px;
      list-style: none;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-lg);
      box-shadow: var(--shadow-pop);
    }

    :host(.inline-results) .results {
      position: static;
      margin-top: 8px;
      max-height: none;
      box-shadow: none;
      border: 0;
      padding: 0;
    }

    .result {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-radius: 10px;
      cursor: pointer;
    }

    .result.active {
      background: var(--brand-soft);
    }

    .result-icon {
      width: 36px;
      height: 36px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      border-radius: 10px;
      background: var(--surface-sunken);
      color: var(--ink-700);
      font-size: 14px;
    }

    .result-icon[data-asset='FX'] { color: var(--positive); }
    .result-icon[data-asset='CRYPTO'] { color: var(--warning); }
    .result-icon[data-asset='EQUITY'] { color: var(--brand); }

    .result-text {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }

    .result-symbol {
      font-weight: 650;
      color: var(--ink-900);
    }

    .result-name {
      font-size: 13px;
      color: var(--ink-500);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* Asset tag with the Not tradable tag under it, so neither covers the symbol */
    .result-tags {
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
    }

    .tag {
      flex-shrink: 0;
      white-space: nowrap;
      padding: 3px 8px;
      border-radius: var(--r-pill);
      background: var(--surface-sunken);
      color: var(--ink-500);
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.02em;
    }

    .tag-warn {
      background: var(--warning-soft);
      color: var(--warning);
    }

    .tag-warn i {
      font-size: 9.5px;
    }

    .no-results {
      padding: 16px 12px;
      color: var(--ink-500);
      font-size: 14px;
    }
  `],
})
export class InstrumentSearchComponent implements AfterViewInit {
  private readonly catalog = inject(InstrumentCatalogService);

  readonly placeholder = input('Search instruments by symbol or name');
  /** Focus the input when shown (e.g. in the search dialog) */
  readonly autofocus = input(false);
  /** Keep results open even when the input is not focused (dialog mode) */
  readonly alwaysShowResults = input(false);
  /** Show the Ctrl K hint in the empty field */
  readonly showShortcut = input(false);
  /** Clear the box after a pick */
  readonly clearOnSelect = input(true);

  @Output() selected = new EventEmitter<Instrument>();
  @Output() dismissed = new EventEmitter<void>();

  @ViewChild('input') private inputRef?: ElementRef<HTMLInputElement>;

  readonly listId = `instrument-results-${nextId++}`;
  readonly query = signal('');
  readonly focused = signal(false);
  readonly activeIndex = signal(0);
  readonly results = computed(() => this.catalog.search(this.query()));
  readonly showResults = computed(() => this.alwaysShowResults() || (this.focused() && this.query().trim().length > 0));

  ngAfterViewInit(): void {
    if (this.autofocus()) {
      queueMicrotask(() => this.inputRef?.nativeElement.focus());
    }
  }

  optionId(index: number): string {
    return `${this.listId}-${index}`;
  }

  onInput(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
  }

  onBlur(): void {
    this.focused.set(false);
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.results().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.focused.set(true);
        if (count) this.activeIndex.set((this.activeIndex() + 1) % count);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (count) this.activeIndex.set((this.activeIndex() - 1 + count) % count);
        break;
      case 'Enter': {
        const pick = this.results()[this.activeIndex()];
        if (this.showResults() && pick) {
          event.preventDefault();
          this.choose(pick);
        }
        break;
      }
      case 'Escape':
        if (this.query()) {
          this.clear();
        } else {
          this.dismissed.emit();
        }
        break;
    }
  }

  choose(instrument: Instrument): void {
    this.selected.emit(instrument);
    if (this.clearOnSelect()) {
      this.query.set('');
    }
    this.activeIndex.set(0);
    this.inputRef?.nativeElement.blur();
  }

  clear(): void {
    this.query.set('');
    this.activeIndex.set(0);
    this.inputRef?.nativeElement.focus();
  }

  assetIcon(instrument: Instrument): string {
    switch (instrument.assetClass) {
      case 'FX':
        return 'fa-money-bill-transfer';
      case 'CRYPTO':
        return 'fa-coins';
      default:
        return 'fa-building';
    }
  }
}
