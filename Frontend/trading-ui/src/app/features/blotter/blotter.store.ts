import { Injectable, computed, signal } from '@angular/core';
import { interval, Subject, takeUntil } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { OrderHistoryService } from './services/order-history.service';
import { BlotterOrder, OrderFilters } from './blotter.models';
import { OrderStatus } from '../../api/generated/trade';

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

  constructor(orderHistoryService?: OrderHistoryService) {
    this.orderHistoryService = orderHistoryService;
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

  // Public computed observables
  readonly orders = computed(() => this.ordersSignal());
  readonly isLoading = computed(() => this.isLoadingSignal());
  readonly isPolling = computed(() => this.isPollingSignal());
  readonly pollAttempts = computed(() => this.pollAttemptsSignal());
  readonly lastError = computed(() => this.lastErrorSignal());
  readonly filters = computed(() => this.filtersSignal());

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
   * Clear all state
   */
  clearOrders(): void {
    this.stopPolling();
    this.ordersSignal.set([]);
    this.pollAttemptsSignal.set(0);
    this.lastErrorSignal.set(null);
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
