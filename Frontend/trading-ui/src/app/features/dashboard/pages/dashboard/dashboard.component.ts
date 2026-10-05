import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PositionsOverviewComponent } from '../../components/positions-overview/positions-overview.component';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { BlotterStore } from '../../../blotter/blotter.store';
import { DashboardService } from '../../services/dashboard.service';
import { OrderStatus, PositionResponse } from '../../../../api/generated/trade';
import { computed } from '@angular/core';

interface StatCard {
  title: string;
  /** Display value; '—' while the figure is still loading */
  value: string;
  /** One line of context under the value */
  caption: string;
  icon: string;
  /** Where the card takes you */
  link: string;
  linkLabel: string;
}

interface RecentTrade {
  id: string;
  symbol: string;
  type: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  time: string;
  status: 'EXECUTED' | 'PENDING' | 'CANCELLED' | 'REJECTED';
}

/**
 * Dashboard Component
 * Main dashboard landing page with portfolio overview and quick actions
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, PositionsOverviewComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private blotterStore = inject(BlotterStore);
  private dashboardService = inject(DashboardService);
  
  currentDate = new Date();

  // Real-time portfolio data signals
  private cashBalanceSignal = signal<number>(0);
  private accountSignal = signal<any>(null);
  /** Null until the holdings panel has loaded positions */
  private positionsSignal = signal<PositionResponse[] | null>(null);

  // Portfolio stats - computed from real data
  accountDisplayId = computed(() => {
    const account = this.accountSignal();
    return account?.accountId || null;
  });
  accountDisplayName = computed(() => {
    const account = this.accountSignal();
    return account?.holderName || null;
  });

  /** Headline figures, all from real account data */
  portfolioStats = computed<StatCard[]>(() => {
    const cashBalance = this.cashBalanceSignal();
    const positions = this.positionsSignal();
    const openOrders = (this.blotterStore.orders() ?? []).filter((o) => o.status === OrderStatus.New).length;
    const holdingsCost = positions?.reduce((sum, p) => sum + (p.quantity || 0) * (p.averageCost || 0), 0) ?? null;

    return [
      {
        title: 'Cash balance',
        value: this.formatAmount(cashBalance || 0),
        caption: 'Available to trade',
        icon: 'fas fa-wallet',
        link: '/transactions',
        linkLabel: 'Deposit or withdraw',
      },
      {
        title: 'Holdings at cost',
        value: holdingsCost === null ? '—' : this.formatAmount(holdingsCost),
        caption:
          positions === null
            ? 'Loading holdings…'
            : positions.length === 0
              ? 'No open positions'
              : `${positions.length} open position${positions.length === 1 ? '' : 's'}`,
        icon: 'fas fa-layer-group',
        link: '/positions',
        linkLabel: 'View positions',
      },
      {
        title: 'Open orders',
        value: String(openOrders),
        caption: openOrders === 0 ? 'Nothing awaiting execution' : 'Awaiting execution',
        icon: 'fas fa-hourglass-half',
        link: '/blotter',
        linkLabel: 'Open order blotter',
      },
    ];
  });

  onPositionsLoaded(positions: PositionResponse[]): void {
    this.positionsSignal.set(positions);
  }

  // Get recent trades from blotter store - real data
  recentTrades = computed(() => {
    const orders = this.blotterStore.orders();
    if (!orders || orders.length === 0) {
      return [];
    }
    
    // Map blotter orders to recent trades, get last 3 orders with any status
    return orders
      .slice(0, 3)
      .map(order => ({
        id: order.orderId,
        symbol: order.symbol,
        type: order.side as 'BUY' | 'SELL',
        quantity: order.quantity,
        price: order.price,
        time: this.formatDateTime(order.createdOn),
        status: this.mapOrderStatus(order.status) as 'EXECUTED' | 'PENDING' | 'CANCELLED' | 'REJECTED'
      }));
  });

  constructor(
    public authService: CoreAuthService
  ) {}

  ngOnInit(): void {
    // Load order history from the account
    const currentUser = this.authService.currentUser();
    if (currentUser?.accountId) {
      this.blotterStore.loadOrderHistory(currentUser.accountId);
      
      // Load dashboard data (balance and account info)
      this.dashboardService.getBalance(currentUser.accountId).subscribe({
        next: (balance) => {
          this.cashBalanceSignal.set(balance.cashBalance);
        },
        error: (err) => {
          console.error('Failed to load balance:', err);
        }
      });

      this.dashboardService.getAccount(currentUser.accountId).subscribe({
        next: (account) => {
          this.accountSignal.set(account);
        },
        error: (err) => {
          console.error('Failed to load account:', err);
        }
      });
    }
  }

  private formatAmount(value: number): string {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }

  private formatDateTime(value: string | number | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }

    const raw = String(value).trim();
    if (!raw) {
      return '';
    }

    const numericPattern = /^-?\d+(\.\d+)?$/;
    if (numericPattern.test(raw)) {
      const numericValue = Number(raw);
      if (Number.isFinite(numericValue)) {
        const epochMs = Math.abs(numericValue) < 1e12 ? numericValue * 1000 : numericValue;
        const epochDate = new Date(epochMs);
        if (!Number.isNaN(epochDate.getTime())) {
          return epochDate.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
          });
        }
      }
    }

    const parsedDate = new Date(raw);
    if (!Number.isNaN(parsedDate.getTime())) {
      return parsedDate.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    }

    return raw;
  }

  private mapOrderStatus(status: string): string {
    switch (status) {
      case 'FILLED':
        return 'EXECUTED';
      case 'NEW':
        return 'PENDING';
      case 'CANCELLED':
        return 'CANCELLED';
      case 'REJECTED':
        return 'REJECTED';
      default:
        return 'PENDING';
    }
  }

  refreshDashboard(): void {
    const currentUser = this.authService.currentUser();
    if (currentUser?.accountId) {
      this.blotterStore.loadOrderHistory(currentUser.accountId);
      
      // Refresh balance data
      this.dashboardService.getBalance(currentUser.accountId).subscribe({
        next: (balance) => {
          this.cashBalanceSignal.set(balance.cashBalance);
        }
      });
    }
  }

  getStatusClass(status: string): string {
    return `status-${status.toLowerCase()}`;
  }

  getStatusIcon(status: string): string {
    switch (status.toUpperCase()) {
      case 'EXECUTED':
        return 'fa-check-circle';
      case 'PENDING':
        return 'fa-hourglass-half';
      case 'CANCELLED':
        return 'fa-times-circle';
      case 'REJECTED':
        return 'fa-ban';
      default:
        return 'fa-circle';
    }
  }
}
