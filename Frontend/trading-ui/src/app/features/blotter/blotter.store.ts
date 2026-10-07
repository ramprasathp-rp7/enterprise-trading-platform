import { Injectable, computed, signal } from '@angular/core';
import { interval, Subject, takeUntil } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { OrderHistoryService } from './services/order-history.service';
import { OrderCancelService } from './services/order-cancel.service';
import { BlotterOrder, CancelResult, OrderFilters } from './blotter.models';
import { OrderStatus } from '../../api/generated/trade';
import { OrderStatusWatcherService } from '../../core/notifications/order-status-watcher.service';

/**
 * Blotter state store
 * Manages blotter list state, filtering, and bounded polling logic
 */
@Injectable({
  providedIn: 'root',
})
export class BlotterStore {
  // OrderHistoryService is injected by Angular when instantiated via DI.
  // Tests instantiate the store with `new BlotterStore()` and then
  // assign a mock to this property, so accept an optional service
  // via constructor but keep a no-arg constructor-compatible form.
  private orderHistoryService?: OrderHistoryService;
  private orderCancelService?: OrderCancelService;
  private orderWatcher?: OrderStatusWatcherService;

  constructor(
    orderHistoryService?: OrderHistoryService,
    orderCancelService?: OrderCancelService,
    orderWatcher?: OrderStatusWatcherService,
  ) {
    this.orderHistoryService = orderHistoryService;
    this.orderCancelService = orderCancelService;
    this.orderWatcher = orderWatcher;
  }

  // Polling configuration - can be customized
  private readonly POLL_INTERVAL_MS = 2000; // Poll every 2 seconds
  private readonly MAX_POLL_ATTEMPTS = 30; // Stop after 30 attempts (1 minute total)

  // State signals
  private ordersSignal = signal<BlotterOrder[]>([]);
  private isLoadingSignal = signal<boolean>(false);
  private isPollingSignal = signal<boolean>(false);
  private pollAttemptsSignal = signal<number>(0);
  private lastErrorSignal = signal<string | null>(null);
  private filtersSignal = signal<OrderFilters>({});
  private cancellingIdsSignal = signal<ReadonlySet<string>>(new Set());
  private cancelResultSignal = signal<CancelResult | null>(null);

  // Public computed observables
  readonly orders = computed(() => this.ordersSignal());
  readonly isLoading = computed(() => this.isLoadingSignal());
  readonly isPolling = computed(() => this.isPollingSignal());
  readonly pollAttempts = computed(() => this.pollAttemptsSignal());
  readonly lastError = computed(() => this.lastErrorSignal());
  readonly filters = computed(() => this.filtersSignal());
  readonly cancellingIds = computed(() => this.cancellingIdsSignal());
  readonly cancelResult = computed(() => this.cancelResultSignal());

  // Computed: filtered orders based on current filters
  readonly filteredOrders = computed(() => {
    const allOrders = this.orders();
    const currentFilters = this.filters();

    return allOrders.filter(order => {
      if (currentFilters.status && order.status !== currentFilters.status) {
        return false;
      }
      if (currentFilters.symbol && !order.symbol.toUpperCase().includes(currentFilters.symbol.toUpperCase())) {
        return false;
      }
      return true;
    });
  });

  // Computed: check if any order is at NEW status
  readonly hasNewOrders = computed(() => {
    return this.orders().some(order => order.status === OrderStatus.New);
  });

  // Control signals for stopping polling
  private stopPolling$ = new Subject<void>();
  private currentAccountId: number | null = null;

  /**
   * Load order history and start polling if needed
   */
  loadOrderHistory(accountId: number): void {
    this.currentAccountId = accountId;
    this.isLoadingSignal.set(true);
    this.lastErrorSignal.set(null);

    this.orderHistoryService!.getOrderHistory(accountId).subscribe({
      next: (orders) => {
        this.ordersSignal.set(orders as BlotterOrder[]);
        this.isLoadingSignal.set(false);

        // Start polling if there are NEW orders
        if (orders.some(o => (o as BlotterOrder).status === OrderStatus.New)) {
          this.startPolling(accountId);
        }
      },
      error: (err) => {
        this.isLoadingSignal.set(false);
        this.lastErrorSignal.set(err?.error?.message || 'Failed to load order history');
      },
    });
  }

  /**
   * Start bounded polling for order history
   * Stops when: (1) nothing is at NEW, (2) max attempts reached
   */
  private startPolling(accountId: number): void {
    if (this.isPollingSignal()) {
      return; // Already polling
    }

    this.isPollingSignal.set(true);
    this.pollAttemptsSignal.set(0);
    this.stopPolling$ = new Subject<void>();

    interval(this.POLL_INTERVAL_MS)
      .pipe(
        takeWhile(() => this.pollAttemptsSignal() < this.MAX_POLL_ATTEMPTS),
        takeUntil(this.stopPolling$),
        switchMap(() =>
          this.orderHistoryService!.getOrderHistory(accountId)
        )
      )
      .subscribe({
        next: (orders) => {
          this.ordersSignal.set(orders as BlotterOrder[]);
          this.pollAttemptsSignal.update(count => count + 1);

          // Stop polling if no more NEW orders
          if (!orders.some(o => (o as BlotterOrder).status === OrderStatus.New)) {
            this.stopPolling();
          }
        },
        error: (err) => {
          this.lastErrorSignal.set(err?.error?.message || 'Polling failed');
          this.stopPolling();
        },
      });
  }

  /**
   * Stop the polling loop
   */
  stopPolling(): void {
    if (this.isPollingSignal()) {
      this.stopPolling$.next();
      this.isPollingSignal.set(false);
    }
  }

  /**
   * Manual refresh: re-fetch order history and restart polling if needed
   */
  refreshOrderHistory(): void {
    if (this.currentAccountId !== null) {
      this.stopPolling();
      this.loadOrderHistory(this.currentAccountId);
    }
  }

  /**
   * Cancel a NEW order via DELETE /api/v1/orders/{id}.
   * On success the row is marked CANCELLED immediately, then the history is
   * re-fetched so the blotter reflects the server's state.
   */
  cancelOrder(orderId: string): void {
    if (!this.orderCancelService || this.cancellingIdsSignal().has(orderId)) {
      return;
    }

    this.setCancelling(orderId, true);
    this.cancelResultSignal.set(null);

    this.orderCancelService.cancelOrder(orderId).subscribe({
      next: (response) => {
        this.setCancelling(orderId, false);
        this.ordersSignal.update(orders =>
          orders.map(order =>
            order.orderId === orderId ? { ...order, status: OrderStatus.Cancelled } : order
          )
        );
        const order = this.ordersSignal().find(o => o.orderId === orderId);
        this.cancelResultSignal.set({
          type: 'success',
          orderId,
          message: response?.message || `${order?.side ?? ''} ${order?.symbol ?? 'Order'} cancelled`.trim(),
        });
        // The order service publishes ORDER_CANCELLED to trade-events; check the feed soon so the
        // bell shows it within seconds (the notification itself only comes from the feed)
        this.orderWatcher?.track(orderId);
        this.syncOrderHistory();
      },
      error: (err) => {
        this.setCancelling(orderId, false);
        this.cancelResultSignal.set({
          type: 'error',
          orderId,
          errorCode: err?.error?.errorCode,
          message: BlotterStore.describeCancelError(err),
        });
        // The order may have moved on (e.g. filled) — re-sync with the server.
        this.syncOrderHistory();
      },
    });
  }

  /**
   * Dismiss the last cancel result banner
   */
  clearCancelResult(): void {
    this.cancelResultSignal.set(null);
  }

  /**
   * Re-fetch history in the background (no loading state) and keep polling
   * while any order is still NEW.
   */
  private syncOrderHistory(): void {
    const accountId = this.currentAccountId;
    if (accountId === null || !this.orderHistoryService) {
      return;
    }

    this.orderHistoryService.getOrderHistory(accountId).subscribe({
      next: (orders) => {
        this.ordersSignal.set(orders as BlotterOrder[]);
        const hasNew = orders.some(o => (o as BlotterOrder).status === OrderStatus.New);
        if (!hasNew) {
          this.stopPolling();
        }
      },
      error: () => {
        // Keep the optimistic state; the next poll or manual refresh will correct it.
      },
    });
  }

  private setCancelling(orderId: string, cancelling: boolean): void {
    this.cancellingIdsSignal.update(ids => {
      const next = new Set(ids);
      if (cancelling) {
        next.add(orderId);
      } else {
        next.delete(orderId);
      }
      return next;
    });
  }

  private static describeCancelError(err: any): string {
    switch (err?.status) {
      case 409:
        return 'This order can no longer be cancelled. It has already been filled, rejected or cancelled.';
      case 404:
        return 'This order could not be found. It may have been removed.';
      case 403:
        return 'Your account is not active, so orders cannot be cancelled.';
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 0:
        return 'Unable to reach the order service. Check your connection and try again.';
      default:
        return err?.error?.message || 'Failed to cancel the order. Please try again.';
    }
  }

  /**
   * Clear all state
   */
  clearOrders(): void {
    this.stopPolling();
    this.ordersSignal.set([]);
    this.pollAttemptsSignal.set(0);
    this.lastErrorSignal.set(null);
    this.cancellingIdsSignal.set(new Set());
    this.cancelResultSignal.set(null);
    this.currentAccountId = null;
  }

  /**
   * Update filters and return filtered orders
   */
  setFilters(filters: OrderFilters): void {
    this.filtersSignal.set(filters);
  }

  /**
   * Clear all filters
   */
  clearFilters(): void {
    this.filtersSignal.set({});
  }
}
