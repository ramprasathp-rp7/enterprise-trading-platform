import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderListRowComponent } from '../order-list-row/order-list-row.component';
import { BlotterOrder } from '../../blotter.models';

/**
 * Order list component
 * Displays orders in a table, newest first
 * Handles empty state gracefully
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, OrderListRowComponent],
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
              <app-order-list-row [order]="order"></app-order-list-row>
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
      padding: 16px;
      width: 100%;
      box-sizing: border-box;
      max-width: none;
    }

    .order-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
      border: 1px solid #e5e7eb;
      border-radius: 6px;
      background: white;
      table-layout: auto;
      box-sizing: border-box;
    }

    thead {
      background-color: #f8fafc;
      border-bottom: 1px solid #e5e7eb;
      position: sticky;
      top: 0;
      z-index: 10;
      display: table;
      width: 100%;
      table-layout: auto;
    }

    thead tr {
      display: table;
      width: 100%;
      table-layout: auto;
    }

    tbody {
      display: table;
      width: 100%;
    }

    tbody tr {
      border-bottom: 1px solid #e5e7eb;
      display: table-row;
      width: 100%;
    }

    tbody tr:hover {
      background-color: #f1f5f9;
    }

    th, td {
      padding: 12px;
      text-align: left;
      border-right: 1px solid #e5e7eb;
      word-wrap: break-word;
      word-break: break-word;
    }

    th {
      font-weight: 600;
      color: #0f172a;
      height: 44px;
      vertical-align: middle;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    td {
      color: #475569;
      vertical-align: top;
      white-space: normal;
      overflow: visible;
    }

    th:last-child, td:last-child {
      border-right: none;
    }

    /* Column widths - flexible distribution */
    th:nth-child(1), td:nth-child(1) { width: 16%; }
    th:nth-child(2), td:nth-child(2) { width: 8%; }
    th:nth-child(3), td:nth-child(3) { width: 7%; }
    th:nth-child(4), td:nth-child(4) { width: 8%; }
    th:nth-child(5), td:nth-child(5) { width: 12%; }
    th:nth-child(6), td:nth-child(6) { width: 12%; }
    th:nth-child(7), td:nth-child(7) { width: 10%; }
    th:nth-child(8), td:nth-child(8) { width: 27%; }

    th:nth-child(3),
    th:nth-child(4),
    th:nth-child(5),
    th:nth-child(6),
    th:nth-child(7),
    th:nth-child(8),
    td:nth-child(3),
    td:nth-child(4),
    td:nth-child(5),
    td:nth-child(6),
    td:nth-child(8) {
      text-align: center;
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
}
