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
        <table class="transactions-table">
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
              <th class="col-quantity">Quantity</th>
              <th class="col-price">Price</th>
              <th class="col-executed">Executed Price</th>
              <th class="col-status">Status</th>
              <th class="col-created">Created</th>
            </tr>
          </thead>
          <tbody>
            @for (order of orders; track order.orderId) {
              <tr class="transaction-row">
                <td class="cell-order-id" [title]="order.orderId"><code>{{ order.orderId }}</code></td>
                <td class="cell-symbol">{{ order.symbol }}</td>
                <td class="cell-side" [class.buy]="order.side === 'BUY'" [class.sell]="order.side === 'SELL'">{{ order.side }}</td>
                <td class="cell-quantity">{{ order.quantity.toLocaleString() }}</td>
                <td class="cell-price">{{ formatCurrency(order.price) }}</td>
                <td class="cell-executed">{{ order.executedPrice != null ? formatCurrency(order.executedPrice) : '—' }}</td>
                <td class="cell-status"><app-order-status-badge [status]="order.status"></app-order-status-badge></td>
                <td class="cell-created"><span class="created-value">{{ formatDate(order.createdOn) }}</span></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <div class="empty-state">
        <p class="empty-message">No orders yet. Place your first order to get started.</p>
      </div>
    }
  `,
  styles: [`
    .table-wrapper {
      background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
      border: 1px solid rgba(226, 232, 240, 0.8);
      border-radius: 14px;
      overflow-y: auto;
      overflow-x: hidden;
      box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
      flex: 1;
      min-height: 360px;
      max-height: min(62vh, 760px);
    }

    .transactions-table {
      width: 100%;
      min-width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 14px;
    }

    .transactions-table col.col-order-id { width: 25%; }
    .transactions-table col.col-symbol { width: 10%; }
    .transactions-table col.col-side { width: 8%; }
    .transactions-table col.col-quantity { width: 9%; }
    .transactions-table col.col-price { width: 10%; }
    .transactions-table col.col-executed { width: 12%; }
    .transactions-table col.col-status { width: 12%; }
    .transactions-table col.col-created { width: 14%; }

    .transactions-table thead {
      background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
      border-bottom: 2px solid rgba(226, 232, 240, 0.6);
    }

    .transactions-table th {
      padding: 14px 18px;
      text-align: left;
      font-weight: 800;
      color: #0f172a;
      font-size: 13px;
      text-transform: uppercase;
      letter-spacing: 0.45px;
      white-space: nowrap;
      position: sticky;
      top: 0;
      z-index: 12;
      background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
      box-shadow: inset 0 -1px 0 rgba(203, 213, 225, 0.85);
    }

    .transactions-table td {
      padding: 14px 18px;
      color: #0f172a;
      vertical-align: middle;
      line-height: 1.35;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .transaction-row {
      border-bottom: 1px solid rgba(226, 232, 240, 0.4);
      transition: all 0.15s ease;
    }

    .transaction-row:hover {
      background-color: rgba(37, 99, 235, 0.02);
    }

    .transaction-row:nth-child(even) {
      background-color: rgba(241, 245, 249, 0.4);
    }

    .transaction-row:nth-child(even):hover {
      background-color: rgba(37, 99, 235, 0.03);
    }

    .cell-side,
    .cell-quantity,
    .cell-price,
    .cell-executed,
    .cell-status,
    .transactions-table th.col-side,
    .transactions-table th.col-quantity,
    .transactions-table th.col-price,
    .transactions-table th.col-executed,
    .transactions-table th.col-status {
      text-align: center;
    }

    .cell-quantity,
    .cell-price,
    .cell-executed {
      font-family: 'Courier New', monospace;
      font-weight: 700;
    }

    .cell-order-id code {
      font-size: 12px;
      color: #7c3aed;
      background: rgba(124, 58, 237, 0.1);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      white-space: normal;
      overflow-wrap: anywhere;
      word-break: break-word;
    }

    .cell-side.buy {
      color: #065f46;
      font-weight: 700;
    }

    .cell-side.sell {
      color: #991b1b;
      font-weight: 700;
    }

    .cell-created {
      color: #64748b;
      font-family: 'Courier New', monospace;
      font-size: 13px;
      white-space: normal;
      overflow: visible;
      text-overflow: clip;
      overflow-wrap: anywhere;
      word-break: break-word;
    }

    .created-value {
      display: inline-block;
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(100, 116, 139, 0.08);
      color: #475569;
      white-space: normal;
    }

    .cell-status {
      white-space: normal;
      overflow: visible;
      text-overflow: clip;
      word-break: normal;
    }

    .empty-state {
      padding: 64px 24px;
      text-align: center;
      color: #64748b;
      border: 1px dashed #cbd5e1;
      border-radius: 12px;
      background: rgba(248, 250, 252, 0.7);
    }

    .empty-message {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
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
