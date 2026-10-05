import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatusBadgeComponent } from '../order-status-badge/order-status-badge.component';
import { BlotterOrder } from '../../blotter.models';

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
            </tr>
          </thead>
          <tbody>
            @for (order of orders; track order.orderId) {
              <tr class="transaction-row">
                <td class="cell-order-id" [title]="order.orderId"><code class="mono-id">{{ order.orderId }}</code></td>
                <td class="cell-symbol cell-strong">{{ order.symbol }}</td>
                <td class="cell-side">
                  <span class="side-text" [class.buy]="order.side === 'BUY'" [class.sell]="order.side === 'SELL'">
                    <i class="fas" [class.fa-arrow-up]="order.side === 'BUY'" [class.fa-arrow-down]="order.side === 'SELL'"></i>
                    {{ order.side }}
                  </span>
                </td>
                <td class="cell-quantity num">{{ order.quantity.toLocaleString() }}</td>
                <td class="cell-price num">{{ formatCurrency(order.price) }}</td>
                <td class="cell-executed num" [class.cell-muted]="order.executedPrice == null">{{ order.executedPrice != null ? formatCurrency(order.executedPrice) : '—' }}</td>
                <td class="cell-status"><app-order-status-badge [status]="order.status"></app-order-status-badge></td>
                <td class="cell-created cell-muted" [title]="formatDate(order.createdOn)">{{ formatDate(order.createdOn) }}</td>
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

    .transactions-table {
      min-width: 980px;
    }

    .transactions-table col.col-order-id { width: 17%; }
    .transactions-table col.col-symbol { width: 10%; }
    .transactions-table col.col-side { width: 9%; }
    .transactions-table col.col-quantity { width: 10%; }
    .transactions-table col.col-price { width: 11%; }
    .transactions-table col.col-executed { width: 11%; }
    .transactions-table col.col-status { width: 13%; }
    .transactions-table col.col-created { width: 19%; }

    /* Separate the right-aligned numbers from the text columns that follow */
    .transactions-table th.col-status,
    .transactions-table .cell-status {
      padding-left: 32px;
    }

    .cell-status {
      overflow: visible;
    }
  `],
})
export class OrderListComponent {
  @Input() orders: BlotterOrder[] | null = null;

  formatCurrency(value: number): string {
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  formatDate(value: string | number | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }

    const raw = String(value).trim();
    if (!raw) {
      return '';
    }

    const numericPattern = /^-?\d+(\.\d+)?$/;
    if (numericPattern.test(raw)) {
      const numericValue = Number(raw);
      if (Number.isFinite(numericValue)) {
        const epochMs = Math.abs(numericValue) < 1e12 ? numericValue * 1000 : numericValue;
        const epochDate = new Date(epochMs);
        if (!Number.isNaN(epochDate.getTime())) {
          return epochDate.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
          });
        }
      }
    }

    const parsedDate = new Date(raw);
    if (!Number.isNaN(parsedDate.getTime())) {
      return parsedDate.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    }

    return raw;
  }
}
