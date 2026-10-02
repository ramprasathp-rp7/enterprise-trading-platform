import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatusBadgeComponent } from '../order-status-badge/order-status-badge.component';
import { BlotterOrder } from '../../blotter.models';
import { OrderStatus } from '../../../../api/generated/trade';

/**
 * Order list row component
 * Displays a single order entry with formatted dates, prices, and status
 */
@Component({
  selector: 'app-order-list-row',
  standalone: true,
  imports: [CommonModule, OrderStatusBadgeComponent],
  template: `
    @if (order) {
      <tr class="order-row" [class.new-status]="order.status === 'NEW'">
        <td class="order-id">{{ order.orderId }}</td>
        <td class="symbol">{{ order.symbol }}</td>
        <td class="side" [class.buy]="order.side === 'BUY'" [class.sell]="order.side === 'SELL'">{{ order.side }}</td>
        <td class="quantity">{{ quantity }}</td>
        <td class="price">{{ price }}</td>
        <td class="executed-price" [class.filled]="order.executedPrice">{{ executedPrice }}</td>
        <td class="status">
          <app-order-status-badge [status]="order.status"></app-order-status-badge>
        </td>
        <td class="created-on">{{ createdOn }}</td>
      </tr>
    }
  `,
  styles: [`
    .order-row {
      border-bottom: 1px solid #e5e7eb;
      background-color: #ffffff;
    }

    .order-row.new-status {
      background-color: #eff6ff;
      font-weight: 500;
    }

    td {
      padding: 12px 16px;
      vertical-align: middle;
      font-size: 0.875rem;
      white-space: normal;
      word-wrap: break-word;
      word-break: break-word;
    }

    .order-id {
      font-family: monospace;
      color: #0066cc;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .symbol {
      font-weight: 600;
    }

    .side {
      text-align: center;
      font-weight: 600;
    }

    .side.buy {
      color: #155724;
    }

    .side.sell {
      color: #721c24;
    }

    .quantity {
      text-align: center;
      font-family: monospace;
    }

    .price {
      text-align: center;
      font-family: monospace;
    }

    .executed-price {
      text-align: center;
      font-family: monospace;
    }

    .executed-price.filled {
      color: #155724;
      font-weight: 600;
    }

    .created-on {
      text-align: left;
      color: #64748b;
      font-size: 0.8rem;
      font-family: 'Courier New', monospace;
    }

    .status {
      text-align: center;
    }
  `],
})
export class OrderListRowComponent {
  @Input() order: BlotterOrder | null = null;

  get quantity(): string {
    if (!this.order) return '';
    return this.order.quantity.toLocaleString();
  }

  get price(): string {
    if (!this.order) return '';
    return this.formatCurrency(this.order.price);
  }

  get executedPrice(): string {
    if (!this.order) return '';
    if (this.order.executedPrice === null || this.order.executedPrice === undefined) {
      return '—';
    }
    return this.formatCurrency(this.order.executedPrice);
  }

  get createdOn(): string {
    if (!this.order) return '';
    const date = new Date(this.order.createdOn);
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const day = String(date.getUTCDate()).padStart(2, '0');
    const year = date.getUTCFullYear();
    const hours = String(date.getUTCHours()).padStart(2, '0');
    const minutes = String(date.getUTCMinutes()).padStart(2, '0');
    const seconds = String(date.getUTCSeconds()).padStart(2, '0');
    return `${month}/${day}/${year} ${hours}:${minutes}:${seconds} UTC`;
  }

  private formatCurrency(value: number): string {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }
}
