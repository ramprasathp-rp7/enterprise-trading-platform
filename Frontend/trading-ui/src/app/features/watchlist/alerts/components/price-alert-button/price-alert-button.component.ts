import { Component, ElementRef, HostListener, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlertDirection, AlertMode } from '../../../../../api/generated/watchlist';
import { LiveQuoteStore } from '../../../../../core/market-data/live-quote.store';
import { MAX_ACTIVE_ALERTS, PriceAlertsStore } from '../../price-alerts.store';
import { formatInstrumentPrice } from '../../../../instruments/services/instrument-catalog.service';

const MENU_WIDTH = 260;
/** Approximate height of the open form, used to decide whether it fits below the bell */
const MENU_HEIGHT = 340;

/**
 * Bell button on a watchlist row. Opens a small form to alert when the instrument's price
 * goes above or below a level, once or on every quote. The threshold starts at the live price.
 */
@Component({
  selector: 'app-price-alert-button',
  standalone: true,
  imports: [FormsModule],
  template: `
    <button
      type="button"
      class="btn btn-ghost btn-sm bell-btn"
      [class.armed]="hasActive()"
      (click)="toggle()"
      [attr.aria-expanded]="open()"
      aria-haspopup="dialog"
      [attr.aria-label]="'Price alert for ' + symbol()"
      [title]="hasActive() ? 'Has an active price alert' : 'Set a price alert'"
      [attr.data-testid]="'alert-btn-' + symbol()"
    >
      <i class="fa-bell" [class.fas]="hasActive()" [class.far]="!hasActive()"></i>
    </button>

    @if (open()) {
      <form
        class="menu anim-pop"
        animate.leave="anim-pop-out"
        role="dialog"
        [style.top.px]="menuPos().top"
        [style.left.px]="menuPos().left"
        [attr.aria-label]="'New price alert for ' + symbol()" (ngSubmit)="submit()" data-testid="alert-form">
        <p class="menu-title">Alert me on {{ symbol() }}</p>
        <p class="now">
          Now
          <strong>{{ price(livePrice()) }}</strong>
        </p>

        <div class="segmented" role="radiogroup" aria-label="Direction">
          <label [class.on]="direction === Direction.Above">
            <input type="radio" name="direction" [value]="Direction.Above" [(ngModel)]="direction" /> Above
          </label>
          <label [class.on]="direction === Direction.Below">
            <input type="radio" name="direction" [value]="Direction.Below" [(ngModel)]="direction" /> Below
          </label>
        </div>

        <label class="field">
          <span>Price</span>
          <input
            class="form-input"
            type="number"
            name="threshold"
            min="0"
            step="any"
            required
            [(ngModel)]="threshold"
            data-testid="alert-threshold"
          />
        </label>

        <label class="field">
          <span>Notify</span>
          <select class="form-input" name="mode" [(ngModel)]="mode" data-testid="alert-mode">
            <option [value]="Mode.Once">Once, then turn off</option>
            <option [value]="Mode.EveryQuote">On every price update past it</option>
          </select>
        </label>

        @if (message(); as m) {
          <p class="menu-msg" [class.error]="m.error" role="status">{{ m.text }}</p>
        }

        <div class="menu-actions">
          <small class="cap">{{ store.activeCount() }}/{{ maxActive }} active</small>
          <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || !valid()" data-testid="alert-create">
            <i class="fas fa-bell"></i> Create alert
          </button>
        </div>
      </form>
    }
  `,
  styles: [`
    :host {
      position: relative;
      display: inline-block;
    }

    .bell-btn.armed {
      color: var(--brand);
    }

    /* Fixed, not absolute: the watchlist table sits in an overflow:auto wrapper that would clip it */
    .menu {
      position: fixed;
      z-index: var(--z-dropdown, 1000);
      width: 260px;
      max-width: calc(100vw - 16px);
      box-sizing: border-box;
      padding: 12px;
      display: grid;
      /* minmax(0, 1fr): long text must wrap, never widen the form */
      grid-template-columns: minmax(0, 1fr);
      gap: 10px;
      /* Reset what the host table cell passes down (nowrap, right/center alignment, row font) */
      white-space: normal;
      text-align: left;
      font-size: 13px;
      font-weight: 400;
      line-height: 1.4;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-md);
      box-shadow: var(--shadow-raised);
    }

    .menu-title {
      margin: 0;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--ink-500);
    }

    .now {
      margin: 0;
      font-size: 13px;
      color: var(--ink-500);
    }

    .now strong {
      margin-left: 4px;
      color: var(--ink-900);
      font-variant-numeric: tabular-nums;
    }

    .segmented {
      position: relative;
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      padding: 3px;
      border-radius: var(--r-pill);
      background: var(--surface-sunken);
    }

    .segmented label {
      padding: 5px 0;
      border-radius: var(--r-pill);
      font-size: 12.5px;
      font-weight: 600;
      text-align: center;
      color: var(--ink-500);
      cursor: pointer;
    }

    .segmented label.on {
      background: var(--surface);
      color: var(--brand);
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
    }

    .segmented input {
      position: absolute;
      opacity: 0;
      pointer-events: none;
    }

    .field {
      display: grid;
      gap: 4px;
      font-size: 12px;
      color: var(--ink-500);
    }

    .field .form-input {
      width: 100%;
      min-width: 0;
      box-sizing: border-box;
      height: 32px;
      font-size: 13px;
    }

    .menu-msg {
      margin: 0;
      overflow-wrap: anywhere;
      font-size: 12.5px;
      color: var(--positive);
    }

    .menu-msg.error {
      color: var(--negative);
    }

    .menu-actions {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .cap {
      font-size: 12px;
      color: var(--ink-500);
    }
  `],
})
export class PriceAlertButtonComponent implements OnInit, OnDestroy {
  readonly store = inject(PriceAlertsStore);
  private readonly liveQuotes = inject(LiveQuoteStore);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly symbol = input.required<string>();

  protected readonly Direction = AlertDirection;
  protected readonly Mode = AlertMode;
  protected readonly maxActive = MAX_ACTIVE_ALERTS;

  readonly open = signal(false);
  readonly busy = signal(false);
  readonly message = signal<{ text: string; error: boolean } | null>(null);

  direction: AlertDirection = AlertDirection.Above;
  mode: AlertMode = AlertMode.Once;
  threshold: number | null = null;

  readonly livePrice = computed(() => this.liveQuotes.quote(this.symbol())?.price ?? null);

  /** A price in the instrument's currency; '—' when unknown */
  price(value: number | null | undefined): string {
    return formatInstrumentPrice(value, this.symbol());
  }
  readonly hasActive = computed(() => this.store.hasActiveAlert(this.symbol()));

  ngOnInit(): void {
    this.store.ensureLoaded();
    // Capture phase: also catches scrolling of inner containers (table wrapper, shell content area)
    document.addEventListener('scroll', this.onAnyScroll, true);
  }

  ngOnDestroy(): void {
    document.removeEventListener('scroll', this.onAnyScroll, true);
  }

  toggle(): void {
    const opening = !this.open();
    if (opening) {
      this.direction = AlertDirection.Above;
      this.mode = AlertMode.Once;
      this.threshold = this.livePrice();
      this.placeMenu();
    }
    this.open.set(opening);
    this.message.set(null);
  }

  /** Viewport position of the form: under the bell, right edges aligned; above it when there is no room below */
  readonly menuPos = signal({ top: 0, left: 0 });

  private placeMenu(): void {
    const button = (this.host.nativeElement as HTMLElement).querySelector('button');
    if (!button) {
      return;
    }
    const rect = button.getBoundingClientRect();
    const gap = 8;
    const left = Math.max(gap, Math.min(rect.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - gap));
    const roomBelow = window.innerHeight - rect.bottom;
    const top = roomBelow >= MENU_HEIGHT + gap ? rect.bottom + gap : Math.max(gap, rect.top - MENU_HEIGHT - gap);
    this.menuPos.set({ top, left });
  }

  valid(): boolean {
    return this.threshold !== null && Number.isFinite(Number(this.threshold)) && Number(this.threshold) > 0;
  }

  submit(): void {
    if (!this.valid() || this.busy()) {
      return;
    }
    const threshold = Number(this.threshold);
    this.busy.set(true);
    this.store.create({ symbol: this.symbol(), direction: this.direction, threshold, mode: this.mode }).subscribe({
      next: () => {
        this.busy.set(false);
        const side = this.direction === AlertDirection.Above ? 'above' : 'below';
        this.message.set({ text: `Alert set: ${this.symbol()} ${side} ${this.price(threshold)}.`, error: false });
      },
      error: (err: Error) => {
        this.busy.set(false);
        this.message.set({ text: err.message, error: true });
      },
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }

  /** A fixed form would drift away from its row, so close it when the window resizes or anything scrolls */
  @HostListener('window:resize')
  onViewportChange(): void {
    this.open.set(false);
  }

  private readonly onAnyScroll = (event: Event): void => {
    // Scrolls inside the form itself (e.g. a select list) don't count
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  };
}
