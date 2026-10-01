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
      overflow-x: auto;
    }

    .order-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
    }

    thead {
      background-color: #f5f5f5;
      border-bottom: 2px solid #ddd;
    }

    th {
      padding: 12px 8px;
      text-align: left;
      font-weight: 600;
      color: #333;
    }

    th:nth-child(3),
    th:nth-child(4),
    th:nth-child(5),
    th:nth-child(6),
    th:nth-child(8) {
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
