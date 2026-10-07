import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatusBadgeComponent } from '../order-status-badge/order-status-badge.component';
import { BlotterOrder } from '../../blotter.models';
import { formatMoney } from '../../../../shared/currency';
import { InstrumentCatalogService } from '../../../instruments/services/instrument-catalog.service';

/**
 * Order list component
 * Displays orders in a table, newest first
 * Handles empty state gracefully
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, OrderStatusBadgeComponent],
  template: `
    @if (orders && orders.length > 0) {
      <div class="table-wrapper">
        <table class="data-table transactions-table">
          <colgroup>
            <col class="col-order-id" />
            <col class="col-symbol" />
            <col class="col-side" />
            <col class="col-quantity" />
            <col class="col-price" />
            <col class="col-executed" />
            <col class="col-status" />
            <col class="col-created" />
            <col class="col-actions" />
          </colgroup>
          <thead>
            <tr>
              <th class="col-order-id">Order ID</th>
              <th class="col-symbol">Symbol</th>
              <th class="col-side">Side</th>
              <th class="col-quantity num">Quantity</th>
              <th class="col-price num">Price</th>
              <th class="col-executed num">Executed</th>
              <th class="col-status">Status</th>
              <th class="col-created">Created</th>
              <th class="col-actions"><span class="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            @for (order of orders; track order.orderId) {
              <tr class="transaction-row">
                <td class="cell-order-id" [title]="order.orderId"><code class="order-id">@for (part of idParts(order.orderId); track $index) {<ng-container>{{ part }}</ng-container><wbr>}</code></td>
                <td class="cell-symbol cell-strong">{{ order.symbol }}</td>
                <td class="cell-side">
                  <span class="side-text" [class.buy]="order.side === 'BUY'" [class.sell]="order.side === 'SELL'">
                    <i class="fas" [class.fa-arrow-up]="order.side === 'BUY'" [class.fa-arrow-down]="order.side === 'SELL'"></i>
                    {{ order.side }}
                  </span>
                </td>
                <td class="cell-quantity num">{{ order.quantity.toLocaleString() }}</td>
                <td class="cell-price num">{{ formatPrice(order.price, order.symbol) }}</td>
                <td class="cell-executed num" [class.cell-muted]="order.executedPrice == null">{{ order.executedPrice != null ? formatPrice(order.executedPrice, order.symbol) : '—' }}</td>
                <td class="cell-status"><app-order-status-badge [status]="order.status"></app-order-status-badge></td>
                <td class="cell-created" [title]="formatDate(order.createdOn)">
                  @if (formatDateParts(order.createdOn); as parts) {
                    <span class="created-date">{{ parts.date }}</span>
                    <span class="created-time">{{ parts.time }}</span>
                  }
                </td>
                <td class="cell-actions">
                  @if (order.status === 'NEW') {
                    @if (cancellingIds.has(order.orderId)) {
                      <span class="cancel-progress"><i class="fas fa-spinner fa-spin"></i> Cancelling…</span>
                    } @else if (confirmingId === order.orderId) {
                      <div class="confirm-group">
                        <button type="button" class="btn btn-sm btn-danger" data-testid="confirm-cancel-btn"
                          (click)="confirmCancel(order.orderId)">Confirm</button>
                        <button type="button" class="btn btn-sm btn-ghost keep-btn" title="Keep order" aria-label="Keep order"
                          (click)="confirmingId = null"><i class="fas fa-xmark"></i></button>
                      </div>
                    } @else {
                      <button type="button" class="btn btn-sm btn-secondary cancel-btn" data-testid="cancel-order-btn"
                        [attr.aria-label]="'Cancel order ' + order.orderId"
                        (click)="confirmingId = order.orderId">
                        <i class="fas fa-xmark"></i> Cancel
                      </button>
                    }
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <div class="empty-state">
        <i class="fas fa-receipt"></i>
        <p class="empty-message">No orders yet. Place your first order to get started.</p>
        <small>Orders matching your filters will be listed here.</small>
      </div>
    }
  `,
  styles: [`
    :host {
      display: block;
    }

    .table-wrapper {
      max-height: min(64vh, 780px);
    }

    /* Columns size to their content so nothing is cut off; the table fills the panel */
    .transactions-table {
      table-layout: auto;
    }

    .transactions-table th,
    .transactions-table td {
      overflow: visible;
      text-overflow: clip;
    }

    /* Full order id: one line when there is room, otherwise wraps at its hyphens */
    .cell-order-id {
      white-space: normal;
    }

    .order-id {
      display: inline-block;
      /* wide enough for the longer half of a UUID, so an id never takes more than two lines */
      min-width: 22ch;
      font-family: var(--font-mono);
      font-size: 12.5px;
      color: var(--ink-700);
      line-height: 1.45;
      /* break only at the <wbr> after each hyphen, never inside a segment */
      overflow-wrap: normal;
      word-break: keep-all;
    }

    /* Dense blotter: tighter inner padding, outer cells keep the 24px panel inset */
    .transactions-table th,
    .transactions-table td {
      padding: 0 14px;
    }

    .transactions-table th:first-child,
    .transactions-table td:first-child {
      padding-left: 24px;
    }

    .transactions-table th:last-child,
    .transactions-table td:last-child {
      padding-right: 24px;
    }

    /* Separate the right-aligned numbers from the text columns that follow */
    .transactions-table th.col-status,
    .transactions-table .cell-status {
      padding-left: 24px;
    }

    /* Narrower screens: tighten inner cell padding so the full row still fits */
    @media (max-width: 1500px) {
      .transactions-table th:not(:first-child):not(:last-child),
      .transactions-table td:not(:first-child):not(:last-child) {
        padding-left: 10px;
        padding-right: 10px;
      }

      .transactions-table th.col-status,
      .transactions-table .cell-status {
        padding-left: 16px;
      }
    }

    .cell-created {
      line-height: 1.3;
    }

    .created-date {
      display: block;
      font-size: 13.5px;
      color: var(--ink-900);
    }

    .created-time {
      display: block;
      font-size: 12px;
      color: var(--ink-500);
      font-variant-numeric: tabular-nums;
    }

    .keep-btn {
      width: var(--control-h-sm);
      padding: 0;
    }

    .cell-status {
      overflow: visible;
    }

    .cell-actions {
      text-align: right;
      overflow: visible;
    }

    .confirm-group {
      display: inline-flex;
      gap: 6px;
    }

    .cancel-btn:hover:not(:disabled) {
      color: var(--negative);
      border-color: var(--negative-line);
      background: var(--negative-soft);
    }

    .cancel-progress {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      font-weight: 600;
      color: var(--ink-500);
    }
  `],
})
export class OrderListComponent {
  private readonly catalog = inject(InstrumentCatalogService);

  @Input() orders: BlotterOrder[] | null = null;
  /** Order IDs with a cancel request in flight */
  @Input() cancellingIds: ReadonlySet<string> = new Set();
  /** Emits the order ID once the user confirms cancellation */
  @Output() cancelOrder = new EventEmitter<string>();

  /** Row currently asking "Confirm / Keep" */
  confirmingId: string | null = null;

  /** Order id split after each hyphen so the browser may wrap it there and nowhere else */
  idParts(orderId: string): string[] {
    return (orderId ?? '').split(/(?<=-)/);
  }

  confirmCancel(orderId: string): void {
    this.confirmingId = null;
    this.cancelOrder.emit(orderId);
  }

  /** A price in the instrument's currency, e.g. ₹1,600.00 (bare number when the currency is unknown) */
  formatCurrency(value: number, currency?: string | null): string {
    return formatMoney(value, currency);
  }

  /** A price in the order's instrument currency at the instrument's precision, e.g. ₹1,600.00 or $1.0842 */
  formatPrice(value: number, symbol: string): string {
    return formatMoney(value, this.catalog.find(symbol)?.currency, { decimals: this.catalog.decimals(symbol) });
  }

  formatDate(value: string | number | null | undefined): string {
    const date = this.parseDate(value);
    if (!date) {
      return value === null || value === undefined ? '' : String(value).trim();
    }
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }

  /** Date and time as separate lines for the compact Created column */
  formatDateParts(value: string | number | null | undefined): { date: string; time: string } | null {
    const date = this.parseDate(value);
    if (!date) {
      const raw = value === null || value === undefined ? '' : String(value).trim();
      return raw ? { date: raw, time: '' } : null;
    }
    return {
      date: date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }),
      time: date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    };
  }

  /** Accepts ISO strings and epoch seconds/milliseconds */
  private parseDate(value: string | number | null | undefined): Date | null {
    if (value === null || value === undefined) {
      return null;
    }

    const raw = String(value).trim();
    if (!raw) {
      return null;
    }

    if (/^-?\d+(\.\d+)?$/.test(raw)) {
      const numericValue = Number(raw);
      if (Number.isFinite(numericValue)) {
        const epochMs = Math.abs(numericValue) < 1e12 ? numericValue * 1000 : numericValue;
        const epochDate = new Date(epochMs);
        if (!Number.isNaN(epochDate.getTime())) {
          return epochDate;
        }
      }
    }

    const parsedDate = new Date(raw);
    return Number.isNaN(parsedDate.getTime()) ? null : parsedDate;
  }
}
