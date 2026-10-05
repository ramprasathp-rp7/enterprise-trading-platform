import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatus } from '../../../../api/generated/trade';

/**
 * Order status badge component
 * Displays order status with color coding and accessibility label
 * 
 * Status colors:
 * - NEW: Blue (working state)
 * - FILLED: Green
 * - REJECTED: Red
 * - CANCELLED: Gray
 */
@Component({
  selector: 'app-order-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [ngClass]="['status-badge', getStatusClass()]" [attr.aria-label]="status">
      {{ status }}
    </span>
  `,
  styles: [`
    /* Pill with a status dot; colours are shared design tokens */
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 26px;
      padding: 0 10px;
      border-radius: var(--r-pill);
      border: 1px solid var(--neutral-line);
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.02em;
      text-transform: uppercase;
      white-space: nowrap;
      line-height: 1;
    }

    .status-badge::before {
      content: '';
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }

    /* NEW - Blue (working state) */
    .status-new {
      background: var(--info-soft);
      border-color: var(--info-line);
      color: var(--info);
    }

    /* FILLED - Green */
    .status-filled {
      background: var(--positive-soft);
      border-color: var(--positive-line);
      color: var(--positive);
    }

    /* REJECTED - Red */
    .status-rejected {
      background: var(--negative-soft);
      border-color: var(--negative-line);
      color: var(--negative);
    }

    /* CANCELLED - Gray */
    .status-cancelled {
      background: var(--neutral-soft);
      border-color: var(--neutral-line);
      color: var(--neutral);
    }
  `],
})
export class OrderStatusBadgeComponent {
  @Input() status: OrderStatus = OrderStatus.New;

  getStatusClass(): string {
    switch (this.status) {
      case OrderStatus.New:
        return 'status-new';
      case OrderStatus.Filled:
        return 'status-filled';
      case OrderStatus.Rejected:
        return 'status-rejected';
      case OrderStatus.Cancelled:
        return 'status-cancelled';
      default:
        return 'status-new';
    }
  }
}
