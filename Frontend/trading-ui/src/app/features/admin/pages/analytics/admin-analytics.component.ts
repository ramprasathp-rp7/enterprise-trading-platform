import { Component, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../services/admin.service';
import { PLATFORM_ACCOUNT_CURRENCY, formatMoney } from '../../../../shared/currency';
import {
  OrderStatusChange,
  TransactionHistoryEntry,
} from '../../../../api/generated/admin';

/**
 * Admin Analytics Dashboard Component
 * Shows key trading metrics derived from complete order and transaction history:
 * - Total Orders
 * - Total Trade Value (sum of BUY + SELL transactions)
 * - Total Fee Value (sum of FEE transactions)
 * - Fill Rate (percentage of orders with FILLED status)
 */
@Component({
  selector: 'app-admin-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-analytics.component.html',
  styleUrls: ['./admin-analytics.component.css'],
})
export class AdminAnalyticsComponent {
  private adminService = inject(AdminService);

  // Filter controls
  dateRange = signal<'7d' | '30d' | '90d' | 'all-time'>('30d');

  // Data signals
  orderHistory = signal<OrderStatusChange[]>([]);
  transactionHistory = signal<TransactionHistoryEntry[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string>('');

  // Computed metrics from order and transaction history
  totalOrders = computed(() => {
    const orders = this.orderHistory();
    // Count unique initial orders (from_status IS NULL = initial state)
    const initialOrders = new Set(
      orders.filter((o) => o.fromStatus === null).map((o) => o.orderId)
    );
    return initialOrders.size;
  });

  totalTradeValue = computed(() => {
    // Sum of all BUY and SELL transaction amounts
    return this.transactionHistory()
      .filter((t) => t.transactionType === 'BUY' || t.transactionType === 'SELL')
      .reduce((sum, t) => sum + Number(t.amount), 0);
  });

  totalFeeValue = computed(() => {
    // Sum of all FEE transaction amounts
    return this.transactionHistory()
      .filter((t) => t.transactionType === 'FEE')
      .reduce((sum, t) => sum + Number(t.amount), 0);
  });

  fillRate = computed(() => {
    const orders = this.orderHistory();
    const filledOrders = new Set(
      orders
        .filter((o) => o.toStatus === 'FILLED')
        .map((o) => o.orderId)
    );
    const uniqueOrders = new Set(orders.map((o) => o.orderId));
    
    if (uniqueOrders.size === 0) return 0;
    return (filledOrders.size / uniqueOrders.size) * 100;
  });

  constructor() {
    // Load analytics data when date range changes
    effect(() => {
      const range = this.dateRange();
      this.loadAnalyticsData(range);
    });
  }

  private loadAnalyticsData(range: '7d' | '30d' | '90d' | 'all-time'): void {
    let fromDate: string | undefined;
    let toDate: string | undefined;

    // Only calculate dates for specific ranges, not for 'all-time'
    if (range !== 'all-time') {
      const endDate = new Date();
      const startDate = new Date();

      // Calculate start date based on range
      if (range === '7d') {
        startDate.setDate(startDate.getDate() - 7);
      } else if (range === '30d') {
        startDate.setMonth(startDate.getMonth() - 1);
      } else {
        startDate.setMonth(startDate.getMonth() - 3);
      }

      fromDate = startDate.toISOString();
      toDate = endDate.toISOString();
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    console.log('Loading analytics data for date range:', range, { fromDate, toDate });

    // Load order and transaction history in parallel
    Promise.all([
      firstValueFrom(
        this.adminService.getCompleteOrderHistory(fromDate, toDate)
      ),
      firstValueFrom(
        this.adminService.getCompleteTransactionHistory(fromDate, toDate)
      ),
    ])
      .then(async ([orders, transactions]) => {
        console.log('Analytics data loaded successfully:', { orders, transactions });
        
        if (!transactions || transactions.length === 0) {
          console.warn('No transactions found in date range. Trying without date range...');
          // Try fetching all transactions without date filter
          const allTransactions = await firstValueFrom(
            this.adminService.getCompleteTransactionHistory()
          );
          console.log('All transactions (no filter):', allTransactions);
          this.orderHistory.set(orders || []);
          this.transactionHistory.set(allTransactions || []);
        } else {
          this.orderHistory.set(orders || []);
          this.transactionHistory.set(transactions || []);
        }
        
        this.isLoading.set(false);
      })
      .catch((error) => {
        console.error('Failed to load analytics data:', error);
        this.errorMessage.set(
          'Failed to load analytics data. Please try again.'
        );
        this.isLoading.set(false);
      });
  }

  /**
   * Trade and fee totals are sums of account-currency transactions across every account: trades settle
   * in the account's currency at the fill FX rate. If accounts in other currencies are introduced,
   * these totals must be grouped by account currency.
   */
  formatCurrency(value: number): string {
    return formatMoney(value, PLATFORM_ACCOUNT_CURRENCY);
  }

  formatPercent(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'percent',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value / 100);
  }
}