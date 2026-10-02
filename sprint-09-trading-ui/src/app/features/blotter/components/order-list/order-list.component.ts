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
      <div class="order-list-container">
        <table class="order-table">
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Symbol</th>
              <th>Side</th>
              <th>Quantity</th>
              <th>Price</th>
              <th>Executed Price</th>
              <th>Status</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            @for (order of orders; track order.orderId) {
              <tr class="order-row" [class.new-status]="order.status === 'NEW'">
                <td class="order-id">{{ order.orderId }}</td>
                <td class="symbol">{{ order.symbol }}</td>
                <td class="side" [class.buy]="order.side === 'BUY'" [class.sell]="order.side === 'SELL'">{{ order.side }}</td>
                <td class="quantity">{{ order.quantity.toLocaleString() }}</td>
                <td class="price">{{ formatCurrency(order.price) }}</td>
                <td class="executed-price" [class.filled]="order.executedPrice !== null && order.executedPrice !== undefined">{{ order.executedPrice != null ? formatCurrency(order.executedPrice) : '—' }}</td>
                <td class="status"><app-order-status-badge [status]="order.status"></app-order-status-badge></td>
                <td class="created-on">{{ formatDate(order.createdOn) }}</td>
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
    .order-list-container {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;
      overflow: auto;
      padding: 16px 12px 0;
      width: 100%;
      box-sizing: border-box;
      max-width: none;
    }

    .order-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.8125rem;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
      background: white;
        table-layout: auto;
      box-sizing: border-box;
      overflow: hidden;
    }

    thead {
      background-color: #ffffff;
      border-bottom: 1px solid #e5e7eb;
      position: sticky;
      top: 0;
      z-index: 10;
    }

    thead tr {
      background-color: #f8fafc;
    }

    tbody tr {
      border-bottom: 1px solid #e5e7eb;
      background-color: #ffffff;
      transition: background-color 0.15s ease;
    }

    tbody tr:hover {
      background-color: #eff6ff;
    }

    th, td {
      padding: 12px 16px;
      text-align: left;
      border-right: none;
      word-wrap: break-word;
      word-break: break-word;
    }

    th {
      font-weight: 600;
      color: #475569;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.02em;
      height: 44px;
      vertical-align: middle;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      background-color: transparent;
    }

    td {
      color: #0f172a;
      vertical-align: middle;
      white-space: normal;
      overflow: visible;
    }

    th:nth-child(1), td:nth-child(1) { width: 30%; }
    th:nth-child(2), td:nth-child(2) { width: 10%; }
    th:nth-child(3), td:nth-child(3) { width: 8%; }
    th:nth-child(4), td:nth-child(4) { width: 8%; }
    th:nth-child(5), td:nth-child(5) { width: 10%; }
    th:nth-child(6), td:nth-child(6) { width: 12%; }
    th:nth-child(7), td:nth-child(7) { width: 7%; }
    th:nth-child(8), td:nth-child(8) { width: 15%; }

    th:nth-child(3),
    th:nth-child(4),
    th:nth-child(5),
    th:nth-child(6),
    th:nth-child(7),
    td:nth-child(3),
    td:nth-child(4),
    td:nth-child(5),
    td:nth-child(6),
    td:nth-child(7) {
      text-align: center;
    }

    th:nth-child(8),
    td:nth-child(8) {
      text-align: left;
    }

    .empty-state {
      padding: 40px 20px;
      text-align: center;
      color: #666;
    }

    .empty-message {
      margin: 0;
      font-size: 1rem;
    }
  `],
})
export class OrderListComponent {
  @Input() orders: BlotterOrder[] | null = null;

  formatCurrency(value: number): string {
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  formatDate(iso: string): string {
    try {
      const date = new Date(iso);
      const month = String(date.getUTCMonth() + 1).padStart(2, '0');
      const day = String(date.getUTCDate()).padStart(2, '0');
      const year = date.getUTCFullYear();
      const hours = String(date.getUTCHours()).padStart(2, '0');
      const minutes = String(date.getUTCMinutes()).padStart(2, '0');
      const seconds = String(date.getUTCSeconds()).padStart(2, '0');
      return `${month}/${day}/${year} ${hours}:${minutes}:${seconds} UTC`;
    } catch {
      return '';
    }
  }
}
