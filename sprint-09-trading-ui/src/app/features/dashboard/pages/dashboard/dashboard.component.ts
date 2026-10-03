import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PositionsOverviewComponent } from '../../components/positions-overview/positions-overview.component';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { BlotterStore } from '../../../blotter/blotter.store';
import { DashboardService } from '../../services/dashboard.service';
import { computed } from '@angular/core';

interface StatCard {
  title: string;
  value: string | number;
  change: number;
  changeType: 'positive' | 'negative' | 'neutral';
  icon: string;
}

interface RecentTrade {
  id: string;
  symbol: string;
  type: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  time: string;
  status: 'EXECUTED' | 'PENDING' | 'CANCELLED';
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

  // Portfolio stats - computed from real data
  accountDisplayId = computed(() => {
    const account = this.accountSignal();
    return account?.accountId || null;
  });
  accountDisplayName = computed(() => {
    const account = this.accountSignal();
    return account?.holderName || null;
  });

  portfolioStats = computed(() => {
    const account = this.accountSignal();
    const cashBalance = this.cashBalanceSignal();

    return [
      {
        title: 'Total Portfolio Value',
        value: this.formatAmount(cashBalance || 0), // TODO: Add position values
        change: 2.5,
        changeType: 'positive' as const,
        icon: 'fas fa-wallet'
      },
      {
        title: 'Today\'s Profit/Loss',
        value: this.formatAmount(12450), // TODO: Calculate from trades
        change: 1.2,
        changeType: 'positive' as const,
        icon: 'fas fa-chart-line'
      },
      {
        title: 'Available Cash',
        value: this.formatAmount(cashBalance || 0),
        change: 0,
        changeType: 'neutral' as const,
        icon: 'fas fa-piggy-bank'
      }
    ];
  });

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
        time: this.formatTime(new Date(order.createdOn)),
        status: this.mapOrderStatus(order.status) as 'EXECUTED' | 'PENDING' | 'CANCELLED'
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

  private formatTime(date: Date): string {
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${hours}:${minutes}:${seconds}`;
  }

  private mapOrderStatus(status: string): string {
    switch (status) {
      case 'FILLED':
        return 'EXECUTED';
      case 'NEW':
        return 'PENDING';
      case 'CANCELLED':
      case 'REJECTED':
        return 'CANCELLED';
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
      default:
        return 'fa-circle';
    }
  }
}
