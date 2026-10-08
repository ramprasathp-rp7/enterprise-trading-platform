import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PositionsOverviewComponent } from '../../components/positions-overview/positions-overview.component';
import { WatchlistWidgetComponent } from '../../../watchlist/components/watchlist-widget/watchlist-widget.component';
import { NetWorthChartComponent } from '../../components/net-worth-chart/net-worth-chart.component';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { BlotterStore } from '../../../blotter/blotter.store';
import { DashboardService, NetWorthPoint } from '../../services/dashboard.service';
import { OrderStatus } from '../../../../api/generated/trade';
import { PortfolioService, PortfolioSummary } from '../../../../api/generated/portfolio';
import { PositionResponse } from '../../../../api/generated/trade';
import { computed } from '@angular/core';
import { formatMoney } from '../../../../shared/currency';

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
  imports: [CommonModule, RouterModule, PositionsOverviewComponent, WatchlistWidgetComponent, NetWorthChartComponent],
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
  /** The account's currency, from the balance endpoint; cash and portfolio value are shown in it */
  private cashCurrencySignal = signal<string | null>(null);
  private accountSignal = signal<any>(null);
  /** Null until the portfolio summary has loaded */
  private portfolioSummarySignal = signal<PortfolioSummary | null>(null);
  private portfolioSummaryFailed = signal(false);

  /** End-of-day net worth snapshots for the chart; null until loaded */
  readonly netWorthHistory = signal<NetWorthPoint[] | null>(null);
  readonly netWorthHistoryFailed = signal(false);

  // Portfolio stats - computed from real data
  accountDisplayId = computed(() => {
    const account = this.accountSignal();
    return account?.accountId || null;
  });
  accountDisplayName = computed(() => {
    const account = this.accountSignal();
    return account?.holderName || null;
  });

  /** "4 holdings · 1,170 units" next to the holdings title, once positions have loaded */
  readonly holdingsSummary = signal<string | null>(null);

  onPositionsLoaded(positions: PositionResponse[]): void {
    const open = positions.filter((p) => (p.quantity ?? 0) > 0);
    if (open.length === 0) {
      this.holdingsSummary.set(null);
      return;
    }
    const units = open.reduce((sum, p) => sum + (p.quantity ?? 0), 0);
    this.holdingsSummary.set(
      `${open.length} holding${open.length === 1 ? '' : 's'}${open.length > 4 ? ' · top 4 shown' : ''} · ${units.toLocaleString('en-US')} units`,
    );
  }

  /** Today's net worth for the chart's last point: the portfolio total (cash + positions), null until loaded */
  netWorth = computed(() => this.portfolioSummarySignal()?.totalValue ?? null);
  netWorthCurrency = computed(() => this.portfolioSummarySignal()?.baseCurrency ?? null);

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
        value: formatMoney(cashBalance || 0, this.cashCurrencySignal()),
        caption: 'Available to trade',
        icon: 'fas fa-wallet',
        link: '/transactions',
        linkLabel: 'Deposit or withdraw',
      },
      {
        title: 'Portfolio value',
        value: portfolioValue === null ? '—' : formatMoney(portfolioValue, summary?.baseCurrency),
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
          this.cashCurrencySignal.set(balance.currency ?? null);
        },
        error: (err) => {
          console.error('Failed to load balance:', err);
        }
      });

      this.loadPortfolioSummary(currentUser.accountId);
      this.loadNetWorthHistory(currentUser.accountId);

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

  /** Daily net worth snapshots (order-service account history) for the chart */
  private loadNetWorthHistory(accountId: number): void {
    this.netWorthHistoryFailed.set(false);
    this.dashboardService.getNetWorthHistory(accountId).subscribe({
      next: (history) => {
        this.netWorthHistory.set(history);
      },
      error: (err) => {
        console.error('Failed to load net worth history:', err);
        this.netWorthHistoryFailed.set(true);
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
          this.cashCurrencySignal.set(balance.currency ?? null);
        }
      });

      this.loadPortfolioSummary(currentUser.accountId);
      this.loadNetWorthHistory(currentUser.accountId);
    }
  }
}
