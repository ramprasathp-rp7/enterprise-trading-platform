import { DatePipe, LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { AlertDirection, AlertMode, PriceAlertResponse, PriceAlertTriggerResponse } from '../../../../../api/generated/watchlist';
import { LiveQuoteStore } from '../../../../../core/market-data/live-quote.store';
import { MAX_ACTIVE_ALERTS, PriceAlertsStore } from '../../price-alerts.store';
import { formatInstrumentPrice } from '../../../../instruments/services/instrument-catalog.service';

type AlertStatus = 'active' | 'triggered' | 'paused';

/**
 * "Price alerts" panel under the selected watchlist: the account's alerts on that list's
 * instruments, with live price, status, pause/resume, delete and trigger history.
 */
@Component({
  selector: 'app-price-alerts-panel',
  standalone: true,
  imports: [DatePipe, LowerCasePipe],
  template: `
    <section class="panel alerts-panel" aria-label="Price alerts" data-testid="price-alerts-panel">
      <header class="panel-header">
        <div>
          <h2 class="panel-title">
            Price alerts
            <span class="count-chip" [class.full]="store.atCap()">{{ store.activeCount() }}/{{ maxActive }} active</span>
          </h2>
          <p class="panel-subtitle">Use the <i class="far fa-bell"></i> on a row to add one. Triggered alerts appear in your notifications.</p>
        </div>
      </header>

      @if (message(); as m) {
        <div class="panel-state">
          <div class="alert" [class.alert-success]="!m.error" [class.alert-danger]="m.error" role="status">
            <i class="fas" [class.fa-check-circle]="!m.error" [class.fa-circle-exclamation]="m.error"></i>
            <p>{{ m.text }}</p>
            <button type="button" class="alert-close" (click)="message.set(null)" aria-label="Dismiss"><i class="fas fa-xmark"></i></button>
          </div>
        </div>
      }

      @if (store.isLoading() && !store.isLoaded()) {
        <div class="loading-state"><i class="fas fa-spinner fa-spin"></i><p>Loading alerts…</p></div>
      } @else if (store.error(); as error) {
        <div class="panel-state">
          <div class="alert alert-danger" role="alert" data-testid="alerts-load-error">
            <i class="fas fa-circle-exclamation"></i>
            <p>{{ error }}</p>
            <button type="button" class="btn btn-outline btn-sm" (click)="store.load()">Retry</button>
          </div>
        </div>
      } @else {
        <div class="table-wrapper">
          <table class="data-table alerts-table">
            <thead>
              <tr>
                <th>Instrument</th>
                <th>Condition</th>
                <th>Notify</th>
                <th class="num">Now</th>
                <th>Status</th>
                <th>Last fired</th>
                <th class="actions-head"><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              @for (alert of visible(); track alert.alertId) {
                <tr [attr.data-testid]="'alert-row-' + alert.alertId">
                  <td><span class="cell-strong">{{ alert.symbol }}</span></td>
                  <td>{{ alert.direction === Direction.Above ? '≥' : '≤' }} {{ price(alert.threshold, alert.symbol) }}</td>
                  <td class="cell-muted">{{ alert.mode === Mode.Once ? 'Once' : 'Every update' }}</td>
                  <td class="num">{{ price(livePrice(alert.symbol), alert.symbol) }}</td>
                  <td>
                    <span class="pill" [attr.data-status]="status(alert)">{{ statusLabel(alert) }}</span>
                  </td>
                  <td class="cell-muted">
                    @if (alert.lastTriggeredAt) {
                      {{ price(alert.lastTriggeredPrice, alert.symbol) }} · {{ alert.lastTriggeredAt | date: 'MMM d, h:mm a' }}
                      @if (alert.triggerCount > 1) { <small>({{ alert.triggerCount }}×)</small> }
                    } @else {
                      Not yet
                    }
                  </td>
                  <td class="actions">
                    <div class="row-actions">
                    @if (alert.triggerCount > 0) {
                      <button type="button" class="btn btn-ghost btn-sm" (click)="toggleHistory(alert)" [attr.aria-expanded]="historyFor() === alert.alertId" title="Trigger history">
                        <i class="fas fa-clock-rotate-left"></i>
                      </button>
                    }
                    <button
                      type="button"
                      class="btn btn-outline btn-sm toggle-btn"
                      [disabled]="busyId() === alert.alertId"
                      (click)="setActive(alert, !alert.active)"
                      [attr.data-testid]="'alert-toggle-' + alert.alertId"
                    >
                      {{ alert.active ? 'Pause' : 'Resume' }}
                    </button>
                    <button
                      type="button"
                      class="btn btn-ghost btn-sm"
                      [disabled]="busyId() === alert.alertId"
                      (click)="remove(alert)"
                      [attr.aria-label]="'Delete alert on ' + alert.symbol"
                      [attr.data-testid]="'alert-delete-' + alert.alertId"
                    >
                      <i class="fas fa-xmark"></i>
                    </button>
                    </div>
                  </td>
                </tr>
                @if (historyFor() === alert.alertId) {
                  <tr class="history-row">
                    <td colspan="7">
                      @if (history() === null) {
                        <span class="cell-muted"><i class="fas fa-spinner fa-spin"></i> Loading history…</span>
                      } @else {
                        <ul class="history">
                          @for (t of history(); track t.triggerId) {
                            <li>
                              <span>{{ t.triggeredAt | date: 'MMM d, h:mm:ss a' }}</span>
                              <span class="reason">{{ t.reason }}</span>
                              <span class="pill" [attr.data-delivery]="t.deliveryStatus">{{ t.deliveryStatus | lowercase }}</span>
                            </li>
                          }
                        </ul>
                      }
                    </td>
                  </tr>
                }
              } @empty {
                <tr>
                  <td colspan="7">
                    <div class="empty-state small">
                      <i class="far fa-bell"></i>
                      <p>No alerts on this watchlist</p>
                      <small>Click the bell on any instrument to be told when it crosses a price.</small>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: [`
    .alerts-panel {
      margin-top: 20px;
    }

    .count-chip.full {
      background: var(--warning-soft);
      color: var(--warning);
    }

    .panel-state {
      padding: 16px 20px;
    }

    .panel-state .alert {
      margin: 0;
    }

    .pill[data-status='active'] {
      background: var(--positive-soft);
      color: var(--positive);
    }

    .pill[data-status='triggered'] {
      background: var(--info-soft);
      color: var(--info);
    }

    .pill[data-status='paused'],
    .pill[data-delivery='PENDING'] {
      background: var(--neutral-soft);
      color: var(--neutral);
    }

    .pill[data-delivery='DELIVERED'] {
      background: var(--positive-soft);
      color: var(--positive);
    }

    .pill[data-delivery='FAILED'] {
      background: var(--negative-soft);
      color: var(--negative);
    }

    /* Columns size to their content so nothing is cut off (the global tables use equal widths) */
    .alerts-table {
      table-layout: auto;
    }

    /* The cell stays a table cell, vertically centred with the row; the buttons sit in a flex
       box inside it. Pause and Resume share one width so the delete buttons line up. */
    .actions {
      width: 1%;
      white-space: nowrap;
      vertical-align: middle;
    }

    .row-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
    }

    .toggle-btn {
      min-width: 82px;
      justify-content: center;
    }

    /* The panel's rounded bottom corners clip the table */
    .alerts-panel {
      overflow: hidden;
    }

    .history-row td {
      background: var(--surface-sunken);
    }

    .history {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 6px;
      font-size: 12.5px;
    }

    .history li {
      display: flex;
      align-items: center;
      gap: 12px;
      color: var(--ink-500);
    }

    .history .reason {
      flex: 1;
      color: var(--ink-900);
      font-variant-numeric: tabular-nums;
    }
  `],
})
export class PriceAlertsPanelComponent implements OnInit {
  readonly store = inject(PriceAlertsStore);
  private readonly liveQuotes = inject(LiveQuoteStore);

  /** Symbols of the selected watchlist */
  readonly symbols = input.required<readonly string[]>();

  protected readonly Direction = AlertDirection;
  protected readonly Mode = AlertMode;
  protected readonly maxActive = MAX_ACTIVE_ALERTS;

  readonly busyId = signal<number | null>(null);
  readonly message = signal<{ text: string; error: boolean } | null>(null);
  readonly historyFor = signal<number | null>(null);
  readonly history = signal<PriceAlertTriggerResponse[] | null>(null);

  readonly visible = computed(() => {
    const symbols = new Set(this.symbols());
    return this.store.alerts().filter((a) => symbols.has(a.symbol));
  });

  ngOnInit(): void {
    // Fresh state on each visit: alerts may have fired (and ONCE alerts turned off) since the last load
    this.store.load();
  }

  /** A price in the alert instrument's currency; '—' when unknown */
  price(value: number | null | undefined, symbol: string): string {
    return formatInstrumentPrice(value, symbol);
  }

  livePrice(symbol: string): number | null {
    return this.liveQuotes.quote(symbol)?.price ?? null;
  }

  status(alert: PriceAlertResponse): AlertStatus {
    if (alert.active) {
      return 'active';
    }
    return alert.deactivatedReason === 'TRIGGERED_ONCE' ? 'triggered' : 'paused';
  }

  statusLabel(alert: PriceAlertResponse): string {
    return { active: 'Active', triggered: 'Triggered', paused: 'Paused' }[this.status(alert)];
  }

  setActive(alert: PriceAlertResponse, active: boolean): void {
    this.busyId.set(alert.alertId);
    this.store.setActive(alert.alertId, active).subscribe({
      next: () => {
        this.busyId.set(null);
        this.message.set({ text: `${active ? 'Resumed' : 'Paused'} the ${alert.symbol} alert.`, error: false });
      },
      error: (err: Error) => {
        this.busyId.set(null);
        this.message.set({ text: err.message, error: true });
      },
    });
  }

  remove(alert: PriceAlertResponse): void {
    this.busyId.set(alert.alertId);
    this.store.delete(alert.alertId).subscribe({
      next: () => {
        this.busyId.set(null);
        if (this.historyFor() === alert.alertId) {
          this.historyFor.set(null);
        }
        this.message.set({ text: `Deleted the ${alert.symbol} alert.`, error: false });
      },
      error: (err: Error) => {
        this.busyId.set(null);
        this.message.set({ text: err.message, error: true });
      },
    });
  }

  toggleHistory(alert: PriceAlertResponse): void {
    if (this.historyFor() === alert.alertId) {
      this.historyFor.set(null);
      return;
    }
    this.historyFor.set(alert.alertId);
    this.history.set(null);
    this.store.triggers(alert.alertId).subscribe({
      next: (triggers) => this.history.set(triggers ?? []),
      error: (err: Error) => {
        this.history.set([]);
        this.message.set({ text: err.message, error: true });
      },
    });
  }
}
