import { Component, OnInit, OnDestroy, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  imports: [CommonModule, OrderListComponent, OrderFiltersComponent],
  template: `
    <div class="blotter-container">
      <header class="blotter-header">
        <h1>Order Blotter</h1>
        <div class="header-actions">
          @if (hasNewOrders()) {
            <span class="new-indicator">
              ⚡ {{ pollAttempts() }}/30 refreshes - {{ isPolling() ? 'Polling...' : 'Ready' }}
            </span>
          }
          <button 
            class="refresh-button" 
            (click)="onRefresh()"
            [disabled]="isLoading()"
            title="Manually refresh order history">
            {{ isLoading() ? '⟳ Refreshing...' : '⟳ Refresh' }}
          </button>
        </div>
      </header>

      @if (lastError()) {
        <div class="error-alert">
          <p>{{ lastError() }}</p>
        </div>
      }

      <div class="blotter-content">
        @if (isLoading()) {
          <div class="loading-state">
            <p>Loading order history...</p>
          </div>
        } @else {
          <app-order-filters (filtersChanged)="onFiltersChanged($event)"></app-order-filters>
          <app-order-list [orders]="filteredOrders()"></app-order-list>
        }
      </div>

      @if (hasNewOrders() && isPolling()) {
        <footer class="blotter-footer">
          <small>Orders at NEW are being polled for updates. Refresh stops automatically when all orders are resolved.</small>
        </footer>
      }
    </div>
  `,
  styles: [`
    .blotter-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: white;
    }

    .blotter-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px;
      border-bottom: 2px solid #eee;
      background: #ffffff;
    }

    h1 {
      margin: 0;
      font-size: 1.5rem;
      color: #333;
    }

    .header-actions {
      display: flex;
      gap: 12px;
      align-items: center;
    }

    .new-indicator {
      padding: 6px 12px;
      background-color: #fff3cd;
      border: 1px solid #ffc107;
      border-radius: 4px;
      font-size: 0.875rem;
      color: #856404;
      font-weight: 600;
    }

    .refresh-button {
      padding: 8px 16px;
      background-color: #007bff;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 600;
      transition: background-color 0.2s;
    }

    .refresh-button:hover:not(:disabled) {
      background-color: #0056b3;
    }

    .refresh-button:disabled {
      background-color: #6c757d;
      cursor: not-allowed;
      opacity: 0.7;
    }

    .error-alert {
      padding: 12px 20px;
      background-color: #f8d7da;
      border: 1px solid #f5c6cb;
      border-radius: 4px;
      color: #721c24;
      margin: 12px 20px 0;
    }

    .error-alert p {
      margin: 0;
    }

    .blotter-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;
      overflow-y: auto;
      overflow-x: auto;
      width: 100%;
    }

    .loading-state {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 200px;
      color: #666;
      font-size: 1rem;
    }

    .blotter-footer {
      padding: 12px 20px;
      background-color: #e7f3ff;
      border-top: 1px solid #b3d9ff;
      font-size: 0.8rem;
      color: #004085;
      text-align: center;
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
  }

  /**
   * Manual refresh: reload order history
   */
  onRefresh(): void {
    this.blotterStore.refreshOrderHistory();
  }

  /**
   * Handle filter changes
   */
  onFiltersChanged(filters: OrderFilters): void {
    this.blotterStore.setFilters(filters);
  }
}
