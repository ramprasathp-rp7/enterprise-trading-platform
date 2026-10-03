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
    <div class="transactions-container">
      <div class="welcome-section">
        <div class="welcome-content">
          <h1 class="welcome-title">Order Blotter</h1>
          <p class="welcome-subtitle">View and filter your complete order activity</p>
        </div>
      </div>

      @if (lastError()) {
        <div class="alert alert-error">
          <div class="alert-content">
            <i class="fas fa-exclamation-circle"></i>
            <div>
              <strong>Unable to load orders</strong>
              <p>{{ lastError() }}</p>
            </div>
          </div>
        </div>
      }

      <div class="history-section">
        <div class="section-header">
          <h2 class="section-title">Order History</h2>
          <div class="header-actions">
            @if (hasNewOrders()) {
              <span class="new-indicator">
                {{ pollAttempts() }}/30 refreshes - {{ isPolling() ? 'Polling...' : 'Ready' }}
              </span>
            }
            <button
              class="refresh-button"
              (click)="onRefresh()"
              [disabled]="isLoading()"
              title="Manually refresh order history">
              {{ isLoading() ? 'Refreshing...' : 'Refresh' }}
            </button>
          </div>
        </div>

        <app-order-filters (filtersChanged)="onFiltersChanged($event)"></app-order-filters>

        @if (isLoading()) {
          <div class="table-wrapper loading-wrapper">
            <div class="loading-state">
              <p>Loading order history...</p>
            </div>
          </div>
        } @else {
          <app-order-list [orders]="filteredOrders()"></app-order-list>
        }
      </div>
    </div>
  `,
  styles: [`
    .transactions-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow-y: auto;
      background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
    }

    .welcome-section {
      padding: 24px 36px;
      background: linear-gradient(135deg, #ffffff 0%, #f8fafc 50%);
      border-bottom: 1px solid rgba(226, 232, 240, 0.6);
    }

    .welcome-title {
      margin: 0 0 8px 0;
      font-size: 30px;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.6px;
    }

    .welcome-subtitle {
      margin: 0;
      font-size: 15px;
      color: #64748b;
      font-weight: 500;
    }

    .alert {
      margin: 20px 36px 0;
      padding: 16px;
      border-radius: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .alert-error {
      background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
      border: 1px solid rgba(220, 38, 38, 0.2);
      color: #991b1b;
    }

    .alert-content {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      flex: 1;
    }

    .history-section {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px 36px;
      min-height: 0;
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }

    .section-title {
      margin: 0;
      font-size: 18px;
      font-weight: 700;
      color: #0f172a;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }

    .header-actions {
      display: flex;
      gap: 12px;
      align-items: center;
    }

    .new-indicator {
      padding: 6px 10px;
      border-radius: 6px;
      font-size: 12px;
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
      border: 1px solid #bfdbfe;
      color: #1d4ed8;
      font-weight: 700;
    }

    .refresh-button {
      border: 1px solid #cbd5e1;
      background: #f8fafc;
      color: #334155;
      border-radius: 6px;
      padding: 7px 12px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .refresh-button:hover:not(:disabled) {
      background: #f1f5f9;
    }

    .refresh-button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .table-wrapper {
      background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
      border: 1px solid rgba(226, 232, 240, 0.8);
      border-radius: 14px;
      overflow: auto;
      box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
      flex: 1;
      min-height: 360px;
      max-height: min(62vh, 760px);
    }

    .loading-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .loading-state {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 200px;
      color: #666;
      font-size: 1rem;
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
