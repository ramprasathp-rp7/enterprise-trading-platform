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
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
      max-width: none;
    }

    /* NEW - Blue (working state) */
    .status-new {
      background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
      color: #1d4ed8;
      border: 1px solid rgba(37, 99, 235, 0.2);
    }

    /* FILLED - Green */
    .status-filled {
      background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
      color: #065f46;
      border: 1px solid rgba(5, 150, 105, 0.2);
    }

    /* REJECTED - Red (uses pattern to avoid color-blind issues) */
    .status-rejected {
      background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
      color: #991b1b;
      border: 1px solid rgba(220, 38, 38, 0.2);
    }

    /* CANCELLED - Gray */
    .status-cancelled {
      background: linear-gradient(135deg, #e2e8f0 0%, #cbd5e1 100%);
      color: #334155;
      border: 1px solid rgba(100, 116, 139, 0.22);
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
