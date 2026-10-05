import { Injectable, inject } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { AccountsService, OrderHistoryEntry, OrderStatus } from '../../api/generated/trade';
import { CoreAuthService } from '../auth/core-auth.service';
import { NotificationService } from './notification.service';
import { orderStatusNotice } from './order-messages';

const POLL_MS = 5_000;
/** Stop watching this long after the last placed order if something stays NEW */
const IDLE_LIMIT_MS = 10 * 60_000;

/**
 * Watches the signed-in customer's orders app-wide and raises a notification
 * when an order changes status (NEW → FILLED / REJECTED / CANCELLED),
 * whichever page the user is on.
 * Polls only while at least one order is NEW.
 */
@Injectable({ providedIn: 'root' })
export class OrderStatusWatcherService {
  private readonly accounts = inject(AccountsService);
  private readonly authService = inject(CoreAuthService);
  private readonly notifications = inject(NotificationService);

  private readonly known = new Map<string, OrderStatus>();
  private polling: Subscription | null = null;
  private lastActivity = 0;
  private started = false;

  /** Learn the current statuses (no notifications), then watch any NEW orders. */
  start(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (this.started || !accountId) {
      return;
    }
    this.started = true;
    this.lastActivity = Date.now();
    this.accounts.getOrders(accountId).subscribe({
      next: (orders) => {
        (orders ?? []).forEach((o) => this.known.set(o.orderId, o.status));
        this.ensurePolling();
      },
      error: () => {
        // The blotter surfaces load errors; the watcher just stays idle.
      },
    });
  }

  /** Called after an order is placed: track it and (re)start polling. */
  track(orderId: string, status: OrderStatus | string): void {
    this.known.set(orderId, status as OrderStatus);
    this.lastActivity = Date.now();
    this.ensurePolling();
  }

  stop(): void {
    this.polling?.unsubscribe();
    this.polling = null;
    this.known.clear();
    this.started = false;
  }

  get isPolling(): boolean {
    return this.polling !== null;
  }

  private ensurePolling(): void {
    if (this.polling || !this.hasNew()) {
      return;
    }
    this.polling = timer(POLL_MS, POLL_MS).subscribe(() => this.poll());
  }

  private stopPolling(): void {
    this.polling?.unsubscribe();
    this.polling = null;
  }

  private poll(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId || Date.now() - this.lastActivity > IDLE_LIMIT_MS) {
      this.stopPolling();
      return;
    }
    this.accounts.getOrders(accountId).subscribe({
      next: (orders) => {
        for (const order of orders ?? []) {
          const previous = this.known.get(order.orderId);
          if (previous && previous !== order.status) {
            this.announce(order);
          }
          this.known.set(order.orderId, order.status);
        }
        if (!this.hasNew()) {
          this.stopPolling();
        }
      },
      error: () => {
        // Transient failure: try again on the next tick.
      },
    });
  }

  private hasNew(): boolean {
    return [...this.known.values()].some((s) => s === OrderStatus.New);
  }

  private announce(order: OrderHistoryEntry): void {
    const notice = orderStatusNotice(order);
    if (notice) {
      this.notifications.notify({
        ...notice,
        link: { path: '/blotter' },
        dedupeKey: `order-status:${order.orderId}:${order.status}`,
      });
    }
  }
}
