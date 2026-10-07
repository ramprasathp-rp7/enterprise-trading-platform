import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AccountsService, OrderStatus } from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { OrderTicketStore } from '../../order-ticket.store';
import { formatInstrumentPrice, instrumentCurrency } from '../../../instruments/services/instrument-catalog.service';
import { formatMoney } from '../../../../shared/currency';

/** What the confirmation page shows about one order */
export interface ConfirmedOrder {
  orderId: string;
  status: OrderStatus | string;
  message?: string;
  symbol: string;
  side: string;
  quantity: number;
  price: number;
}

/**
 * Order confirmation page
 *
 * Shown after an order is placed: the status returned at submission, the order
 * details, and a route to the Order Blotter where the live status is tracked.
 * Uses the just-submitted result when available; on a reload or a shared link it
 * looks the order up in the account's order history instead.
 */
@Component({
  selector: 'app-order-confirmation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './order-confirmation.component.html',
  styleUrls: ['./order-confirmation.component.css'],
})
export class OrderConfirmationComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly store = inject(OrderTicketStore);
  private readonly authService = inject(CoreAuthService);
  private readonly accountsService = inject(AccountsService);

  readonly orderId = signal<string>('');
  readonly order = signal<ConfirmedOrder | null>(null);
  readonly loading = signal(false);
  readonly notFound = signal(false);

  readonly status = computed(() => String(this.order()?.status ?? '').toUpperCase());
  readonly isRejected = computed(() => this.status() === 'REJECTED');
  readonly isWorking = computed(() => this.status() === 'NEW');
  readonly notional = computed(() => {
    const order = this.order();
    return order ? Number(order.quantity) * Number(order.price) : null;
  });

  /** The limit price in the instrument's currency */
  formatPrice(value: number | string | null | undefined, symbol: string): string {
    return formatInstrumentPrice(value == null ? null : Number(value), symbol);
  }

  /** The notional in the instrument's currency (cash moves in the account's, at the rate at fill) */
  formatAmount(value: number | null, symbol: string): string {
    return formatMoney(value, instrumentCurrency(symbol));
  }

  readonly title = computed(() => {
    switch (this.status()) {
      case 'NEW':
        return 'Order accepted and working';
      case 'FILLED':
        return 'Order filled';
      case 'REJECTED':
        return 'Order rejected';
      case 'CANCELLED':
        return 'Order cancelled';
      default:
        return 'Order placed';
    }
  });

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('orderId') ?? '';
    this.orderId.set(orderId);

    const submitted = this.store.submissionResult();
    if (submitted && sameOrder(submitted.orderId, orderId)) {
      this.order.set({ ...submitted });
      return;
    }

    this.lookUpOrder(orderId);
  }

  private lookUpOrder(orderId: string): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!orderId || !accountId) {
      this.notFound.set(true);
      return;
    }

    this.loading.set(true);
    this.accountsService.getOrders(accountId).subscribe({
      next: (orders) => {
        const match = (orders ?? []).find((o) => sameOrder(o.orderId, orderId));
        if (match) {
          this.order.set({
            orderId: match.orderId,
            status: match.status,
            symbol: match.symbol,
            side: match.side,
            quantity: match.quantity,
            price: match.price,
          });
        } else {
          this.notFound.set(true);
        }
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }
}

/** Order IDs may carry an `ORD-` display prefix; compare on the bare UUID. */
function sameOrder(a: string | null | undefined, b: string | null | undefined): boolean {
  const bare = (id: string | null | undefined) => (id ?? '').trim().replace(/^ORD-/i, '').toLowerCase();
  return !!bare(a) && bare(a) === bare(b);
}
