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
    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.875rem;
      font-weight: 600;
      white-space: nowrap;
    }

    /* NEW - Blue (working state) */
    .status-new {
      background-color: #d1ecf1;
      color: #0c5460;
      border: 1px solid #bee5eb;
    }

    /* FILLED - Green */
    .status-filled {
      background-color: #d4edda;
      color: #155724;
      border: 1px solid #c3e6cb;
    }

    /* REJECTED - Red (uses pattern to avoid color-blind issues) */
    .status-rejected {
      background-color: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
    }

    /* CANCELLED - Gray */
    .status-cancelled {
      background-color: #e2e3e5;
      color: #383d41;
      border: 1px solid #d6d8db;
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
