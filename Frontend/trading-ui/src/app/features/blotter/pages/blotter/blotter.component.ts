import { Component, OnInit, OnDestroy, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { BlotterStore } from '../../blotter.store';
import { OrderListComponent } from '../../components/order-list/order-list.component';
import { OrderFiltersComponent } from '../../components/order-filters/order-filters.component';
import { OrderFilters } from '../../blotter.models';

/**
 * Blotter page component
 * 
 * Displays all orders for the account with:
 * - Newest first
 * - All statuses (NEW, FILLED, REJECTED, CANCELLED)
 * - Status badges with color coding
 * - Bounded polling while orders are NEW
 * - Manual refresh button
 * - Polling status indicator
 */
@Component({
  selector: 'app-blotter',
  standalone: true,
  imports: [CommonModule, RouterModule, OrderListComponent, OrderFiltersComponent],
  template: `
    <div class="page page-wide transactions-container">
      <header class="page-header">
        <div class="page-heading">
          <h1 class="page-title">Order Blotter</h1>
          <p class="page-subtitle">View and filter your complete order activity</p>
        </div>
        <div class="page-actions">
          @if (hasNewOrders()) {
            <span class="live-indicator" [class.active]="isPolling()">
              <span class="live-dot"></span>
              {{ isPolling() ? 'Live updates' : 'Updates paused' }} · {{ pollAttempts() }}/30
            </span>
          }
          <button
            class="btn btn-secondary refresh-button"
            data-testid="refresh-btn"
            (click)="onRefresh()"
            [disabled]="isLoading()"
            title="Manually refresh order history">
            <i class="fas fa-rotate-right" [class.fa-spin]="isLoading()"></i>
            {{ isLoading() ? 'Refreshing...' : 'Refresh' }}
          </button>
          <a routerLink="/order-ticket" class="btn btn-primary"><i class="fas fa-plus"></i> New order</a>
        </div>
      </header>

      @if (lastError()) {
        <div class="alert alert-error" role="alert">
          <i class="fas fa-exclamation-circle"></i>
          <div>
            <strong>Unable to load orders</strong>
            <p>{{ lastError() }}</p>
          </div>
        </div>
      }

      @if (cancelResult(); as result) {
        <div class="alert" role="status" aria-live="polite" data-testid="cancel-result"
          [class.alert-success]="result.type === 'success'"
          [class.alert-error]="result.type === 'error'">
          <i class="fas" [class.fa-circle-check]="result.type === 'success'" [class.fa-circle-exclamation]="result.type === 'error'"></i>
          <div>
            <strong>{{ result.type === 'success' ? 'Order cancelled' : 'Cancel failed' }}</strong>
            <p>{{ result.message }}</p>
          </div>
          <button class="alert-close" type="button" aria-label="Dismiss" (click)="onDismissCancelResult()">
            <i class="fas fa-times"></i>
          </button>
        </div>
      }

      <section class="panel">
        <header class="panel-header">
          <h2 class="panel-title">Order history <span class="count-chip">{{ filteredOrders().length }}</span></h2>
          <app-order-filters (filtersChanged)="onFiltersChanged($event)"></app-order-filters>
        </header>

        @if (isLoading()) {
          <div class="loading-state">
            <div class="spinner"></div>
            <p>Loading order history...</p>
          </div>
        } @else {
          <app-order-list
            [orders]="filteredOrders()"
            [cancellingIds]="cancellingIds()"
            (cancelOrder)="onCancelOrder($event)">
          </app-order-list>
        }
      </section>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      overflow: hidden;
    }

    .live-indicator {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      height: 32px;
      padding: 0 12px;
      border-radius: var(--r-pill);
      border: 1px solid var(--line);
      background: var(--surface);
      color: var(--ink-500);
      font-size: 12.5px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .live-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--ink-400);
    }

    .live-indicator.active {
      border-color: var(--info-line);
      background: var(--info-soft);
      color: var(--info);
    }

    .live-indicator.active .live-dot {
      background: var(--brand);
      animation: live-pulse 1.4s ease-in-out infinite;
    }

    @keyframes live-pulse {
      0%, 100% { box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.45); }
      50% { box-shadow: 0 0 0 5px rgba(37, 99, 235, 0); }
    }
  `],
})
export class BlotterComponent implements OnInit, OnDestroy {
  private authService = inject(CoreAuthService);
  private blotterStore = inject(BlotterStore);

  // Public computed signals from store
  readonly orders = computed(() => this.blotterStore.orders());
  readonly filteredOrders = computed(() => this.blotterStore.filteredOrders());
  readonly isLoading = computed(() => this.blotterStore.isLoading());
  readonly isPolling = computed(() => this.blotterStore.isPolling());
  readonly pollAttempts = computed(() => this.blotterStore.pollAttempts());
  readonly lastError = computed(() => this.blotterStore.lastError());
  readonly hasNewOrders = computed(() => this.blotterStore.hasNewOrders());
  readonly cancellingIds = computed(() => this.blotterStore.cancellingIds());
  readonly cancelResult = computed(() => this.blotterStore.cancelResult());

  ngOnInit(): void {
    // Load order history for current account
    const currentUser = this.authService.currentUser();
    if (currentUser?.accountId) {
      this.blotterStore.loadOrderHistory(currentUser.accountId);
    } else {
      const accessToken = this.authService.accessToken();
      if (accessToken) {
        try {
          const decoded = this.authService.decodeJWT(accessToken);
          if (decoded.accountId) {
            this.blotterStore.loadOrderHistory(decoded.accountId);
          }
        } catch (err) {
          console.error('Failed to decode JWT', err);
        }
      }
    }
  }

  ngOnDestroy(): void {
    // Stop polling when component is destroyed
    this.blotterStore.stopPolling();
    this.blotterStore.clearCancelResult();
  }

  /**
   * Manual refresh: reload order history
   */
  onRefresh(): void {
    this.blotterStore.refreshOrderHistory();
  }

  /**
   * Cancel a NEW order (already confirmed in the row)
   */
  onCancelOrder(orderId: string): void {
    this.blotterStore.cancelOrder(orderId);
  }

  onDismissCancelResult(): void {
    this.blotterStore.clearCancelResult();
  }

  /**
   * Handle filter changes
   */
  onFiltersChanged(filters: OrderFilters): void {
    this.blotterStore.setFilters(filters);
  }
}
