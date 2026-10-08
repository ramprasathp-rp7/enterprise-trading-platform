import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatusBadgeComponent } from '../order-status-badge/order-status-badge.component';
import { BlotterOrder } from '../../blotter.models';
import { OrderStatus } from '../../../../api/generated/trade';
import { formatInstrumentPrice } from '../../../instruments/services/instrument-catalog.service';

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
      background-color: var(--surface);
      transition: background-color var(--t-fast);
    }

    .order-row:hover {
      background-color: var(--surface-hover);
    }

    .order-row.new-status {
      background-color: var(--info-soft);
    }

    td {
      height: 56px;
      padding: 0 20px;
      vertical-align: middle;
      border-bottom: 1px solid var(--line);
      font-size: 14px;
      color: var(--ink-900);
      white-space: nowrap;
    }

    .order-id {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-family: var(--font-mono);
      font-size: 12.5px;
      color: var(--ink-700);
    }

    .symbol {
      font-weight: 600;
    }

    .side {
      font-weight: 600;
      font-size: 13px;
    }

    .side.buy {
      color: var(--positive);
    }

    .side.sell {
      color: var(--negative);
    }

    .quantity,
    .price,
    .executed-price {
      text-align: right;
      font-variant-numeric: tabular-nums;
      font-weight: 500;
    }

    .executed-price.filled {
      color: var(--positive);
      font-weight: 600;
    }

    .created-on {
      color: var(--ink-500);
      font-size: 13px;
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
    return formatInstrumentPrice(this.order.price, this.order.symbol);
  }

  get executedPrice(): string {
    if (!this.order) return '';
    if (this.order.executedPrice === null || this.order.executedPrice === undefined) {
      return '—';
    }
    return formatInstrumentPrice(this.order.executedPrice, this.order.symbol);
  }

  get createdOn(): string {
    if (!this.order) return '';
    return this.formatTimestamp(this.order.createdOn);
  }

  private formatTimestamp(value: string | number | null | undefined): string {
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
