import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PositionsOverviewComponent } from '../../components/positions-overview/positions-overview.component';
import { WatchlistWidgetComponent } from '../../../watchlist/components/watchlist-widget/watchlist-widget.component';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { BlotterStore } from '../../../blotter/blotter.store';
import { DashboardService } from '../../services/dashboard.service';
import { OrderStatus } from '../../../../api/generated/trade';
import { PortfolioService, PortfolioSummary } from '../../../../api/generated/porfolio';
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

/**
 * Dashboard Component
 * Main dashboard landing page with portfolio overview and quick actions
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, PositionsOverviewComponent, WatchlistWidgetComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private blotterStore = inject(BlotterStore);
  private dashboardService = inject(DashboardService);
  private portfolioService = inject(PortfolioService);
  
  currentDate = new Date();

  // Real-time portfolio data signals
  private cashBalanceSignal = signal<number>(0);
  private accountSignal = signal<any>(null);
  /** Null until the portfolio summary has loaded */
  private portfolioSummarySignal = signal<PortfolioSummary | null>(null);
  private portfolioSummaryFailed = signal(false);

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
    const summary = this.portfolioSummarySignal();
    const openOrders = (this.blotterStore.orders() ?? []).filter((o) => o.status === OrderStatus.New).length;
    const portfolioValue = summary?.totalValue ?? null;
    const positionCount = summary?.positionCount ?? 0;

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
        title: 'Portfolio value',
        value: portfolioValue === null ? '—' : this.formatAmount(portfolioValue),
        caption:
          summary === null
            ? this.portfolioSummaryFailed()
              ? 'Portfolio value unavailable'
              : 'Loading portfolio…'
            : positionCount === 0
              ? 'Cash only · no open positions'
              : `Cash + ${positionCount} open position${positionCount === 1 ? '' : 's'}`,
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

      this.loadPortfolioSummary(currentUser.accountId);

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

  /** Portfolio value (cash plus market value) comes from the portfolio summary endpoint. */
  private loadPortfolioSummary(accountId: number): void {
    this.portfolioSummaryFailed.set(false);
    this.portfolioService.getPortfolioSummary(accountId).subscribe({
      next: (summary) => {
        this.portfolioSummarySignal.set(summary ?? null);
      },
      error: (err) => {
        console.error('Failed to load portfolio summary:', err);
        this.portfolioSummaryFailed.set(true);
      }
    });
  }

  private formatAmount(value: number): string {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
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

      this.loadPortfolioSummary(currentUser.accountId);
    }
  }
}
