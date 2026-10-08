import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from '../../services/dashboard.service';
import { BlotterStore } from '../../../blotter/blotter.store';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { AccountsService } from '../../../../api/generated/trade';
import { WatchlistsService } from '../../../../api/generated/watchlist';
import { PortfolioService, PortfolioSummary } from '../../../../api/generated/portfolio';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let dashboardService: {
    getBalance: ReturnType<typeof vi.fn>;
    getAccount: ReturnType<typeof vi.fn>;
    getNetWorthHistory: ReturnType<typeof vi.fn>;
  };
  let blotterStore: { orders: ReturnType<typeof signal<any[]>>; loadOrderHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let watchlistsApi: { getWatchlists: ReturnType<typeof vi.fn> };
  let portfolio: { getPortfolioSummary: ReturnType<typeof vi.fn> };

  const summary: PortfolioSummary = {
    accountId: 7, baseCurrency: 'USD', cashBalance: 48250.75, marketValue: 1600, costBasis: 1501,
    unrealisedPnl: 99, unrealisedPnlPercent: 6.6, realisedPnl: 0, totalValue: 49850.75,
    positionCount: 2, partial: false, asOf: '2026-10-05T08:00:00Z',
  };

  const orders = [
    { orderId: 'o1', symbol: 'AAPL', side: 'BUY', quantity: 100, price: 189.35, status: 'FILLED', createdOn: '2026-10-05T09:30:00Z' },
    { orderId: 'o2', symbol: 'MSFT', side: 'SELL', quantity: 25, price: 352.1, status: 'NEW', createdOn: 1790000000 },
    { orderId: 'o3', symbol: 'NVDA', side: 'BUY', quantity: 50, price: 471.8, status: 'REJECTED', createdOn: 'not-a-date' },
    { orderId: 'o4', symbol: 'INFY.NS', side: 'BUY', quantity: 10, price: 1500, status: 'CANCELLED', createdOn: null },
  ];

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: dashboardService },
        { provide: BlotterStore, useValue: blotterStore },
        { provide: CoreAuthService, useValue: { currentUser } },
        { provide: AccountsService, useValue: { getPositions: vi.fn(() => of([])) } },
        { provide: WatchlistsService, useValue: watchlistsApi },
        { provide: PortfolioService, useValue: portfolio },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    dashboardService = {
      getBalance: vi.fn(() => of({ accountId: 7, cashBalance: 48250.75, currency: 'USD', asOf: '2026-10-05T08:00:00Z' })),
      getAccount: vi.fn(() => of({ id: 1, accountId: 'ACC-000007', holderName: 'Rahul Kumar', cashBalance: 48250.75, status: 'ACTIVE', version: 1, lastUpdated: '' })),
      getNetWorthHistory: vi.fn(() => of([])),
    };
    blotterStore = { orders: signal<any[]>(orders), loadOrderHistory: vi.fn() };
    currentUser = vi.fn(() => ({ accountId: 7 }));
    watchlistsApi = { getWatchlists: vi.fn(() => of([])) };
    portfolio = { getPortfolioSummary: vi.fn(() => of({ ...summary, costBasis: 0, positionCount: 0 })) };
  });

  it('shows the first watchlist with a link to the full Watchlists page', async () => {
    watchlistsApi.getWatchlists.mockReturnValue(
      of([
        {
          watchlistId: 3,
          accountId: 7,
          name: 'Tech',
          createdAt: '2026-10-01T00:00:00Z',
          updatedAt: '2026-10-01T00:00:00Z',
          items: [{ watchlistItemId: 1, watchlistId: 3, accountId: 7, symbol: 'MSFT', createdAt: '2026-10-01T00:00:00Z' }],
        },
      ]),
    );
    await create();

    expect(watchlistsApi.getWatchlists).toHaveBeenCalledWith(7);
    const panel: HTMLElement = fixture.nativeElement.querySelector('.watchlist-panel');
    expect(panel.textContent).toContain('MSFT');
    expect(panel.textContent).toContain('Microsoft Corporation');
    expect(panel.querySelector('[data-testid="watchlist-view-all"]')!.getAttribute('href')).toBe('/watchlists?id=3');
  });

  it('invites the user to create a watchlist when there are none', async () => {
    await create();

    expect(fixture.nativeElement.querySelector('[data-testid="watchlist-widget-empty"]')).not.toBeNull();
  });

  it('loads orders, balance and account for the signed-in user', async () => {
    await create();

    expect(blotterStore.loadOrderHistory).toHaveBeenCalledWith(7);
    expect(dashboardService.getBalance).toHaveBeenCalledWith(7);
    expect(dashboardService.getAccount).toHaveBeenCalledWith(7);
    expect(component.accountDisplayName()).toBe('Rahul Kumar');
    expect(component.accountDisplayId()).toBe('ACC-000007');
    expect(fixture.nativeElement.querySelector('.page-title').textContent).toContain('Welcome back, Rahul Kumar');
  });

  it('shows real KPIs: cash balance, portfolio value and open orders, each linking onward', async () => {
    await create();

    const stats = component.portfolioStats();
    expect(stats.map((s) => s.title)).toEqual(['Cash balance', 'Portfolio value', 'Open orders']);
    expect(stats[0].value).toBe('$48,250.75');
    // the portfolio summary (mocked) has no positions; one of the mocked orders is NEW
    expect(stats[1]).toEqual(expect.objectContaining({ value: '$49,850.75', caption: 'Cash only · no open positions' }));
    expect(stats[2]).toEqual(expect.objectContaining({ value: '1', caption: 'Awaiting execution' }));

    const cards = fixture.nativeElement.querySelectorAll('a.metric-card');
    expect(cards.length).toBe(3);
    expect(Array.from(cards).map((c: any) => c.getAttribute('href'))).toEqual(['/transactions', '/positions', '/blotter']);
  });

  it('shows the portfolio summary total value (cash plus market value)', async () => {
    portfolio.getPortfolioSummary.mockReturnValue(of(summary));
    await create();

    expect(portfolio.getPortfolioSummary).toHaveBeenCalledWith(7);
    expect(component.portfolioStats()[1]).toEqual(expect.objectContaining({ value: '$49,850.75', caption: 'Cash + 2 open positions' }));

    (component as any).portfolioSummarySignal.set({ ...summary, positionCount: 1 });
    expect(component.portfolioStats()[1].caption).toBe('Cash + 1 open position');
  });

  it('shows "—" for portfolio value until the summary loads', async () => {
    await create();

    (component as any).portfolioSummarySignal.set(null);
    expect(component.portfolioStats()[1]).toEqual(expect.objectContaining({ value: '—', caption: 'Loading portfolio…' }));
  });

  it('marks portfolio value unavailable when the summary fails', async () => {
    portfolio.getPortfolioSummary.mockReturnValue(throwError(() => new Error('503')));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    await create();

    expect(component.portfolioStats()[1]).toEqual(expect.objectContaining({ value: '—', caption: 'Portfolio value unavailable' }));
    consoleError.mockRestore();
  });

  it('loads the net worth history for the chart', async () => {
    const history = [{ date: new Date('2026-10-04T00:00:00Z'), value: 49_000 }];
    dashboardService.getNetWorthHistory.mockReturnValue(of(history));
    await create();

    expect(dashboardService.getNetWorthHistory).toHaveBeenCalledWith(7);
    expect(component.netWorthHistory()).toEqual(history);
    expect(component.netWorthHistoryFailed()).toBe(false);
  });

  it('flags the net worth history as failed when it cannot load', async () => {
    dashboardService.getNetWorthHistory.mockReturnValue(throwError(() => new Error('503')));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    await create();

    expect(component.netWorthHistory()).toBeNull();
    expect(component.netWorthHistoryFailed()).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="net-worth-state"]').textContent).toContain('unavailable');
    consoleError.mockRestore();
  });

  it('says nothing is pending when there are no NEW orders', async () => {
    blotterStore.orders.set([orders[0]]);
    await create();

    expect(component.portfolioStats()[2]).toEqual(expect.objectContaining({ value: '0', caption: 'Nothing awaiting execution' }));
  });

  it('skips all loading without a signed-in account', async () => {
    currentUser.mockReturnValue(null);
    await create();

    expect(blotterStore.loadOrderHistory).not.toHaveBeenCalled();
    expect(dashboardService.getBalance).not.toHaveBeenCalled();
    expect(portfolio.getPortfolioSummary).not.toHaveBeenCalled();
    expect(dashboardService.getNetWorthHistory).not.toHaveBeenCalled();
    expect(component.portfolioStats()[0].value).toBe('0.00');
    expect(component.accountDisplayName()).toBeNull();
  });

  it('logs and keeps defaults when balance or account requests fail', async () => {
    dashboardService.getBalance.mockReturnValue(throwError(() => new Error('down')));
    dashboardService.getAccount.mockReturnValue(throwError(() => new Error('down')));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await create();

    expect(consoleError).toHaveBeenCalledTimes(2);
    expect(component.portfolioStats()[0].value).toBe('0.00');
    expect(component.accountDisplayId()).toBeNull();
    consoleError.mockRestore();
  });

  it('refreshDashboard reloads orders and balance', async () => {
    await create();
    dashboardService.getBalance.mockReturnValue(of({ accountId: 7, cashBalance: 100, currency: 'USD', asOf: '' }));

    component.refreshDashboard();

    expect(blotterStore.loadOrderHistory).toHaveBeenCalledTimes(2);
    expect(portfolio.getPortfolioSummary).toHaveBeenCalledTimes(2);
    expect(dashboardService.getNetWorthHistory).toHaveBeenCalledTimes(2);
    expect(component.portfolioStats()[0].value).toBe('$100.00');
  });

  it('refreshDashboard does nothing without an account', async () => {
    await create();
    currentUser.mockReturnValue(null);

    component.refreshDashboard();

    expect(blotterStore.loadOrderHistory).toHaveBeenCalledTimes(1);
  });


  it('summarises the holdings next to the panel title', async () => {
    await create();
    component.onPositionsLoaded([
      { symbol: 'AAPL', quantity: 300, averageCost: 180 },
      { symbol: 'MSFT', quantity: 1200, averageCost: 350 },
      { symbol: 'OLD', quantity: 0, averageCost: 10 },
    ] as never);
    fixture.detectChanges();

    expect(component.holdingsSummary()).toBe('2 holdings · 1,500 units');
    expect(fixture.nativeElement.querySelector('[data-testid="holdings-summary"]').textContent).toContain('2 holdings');

    component.onPositionsLoaded([]);
    expect(component.holdingsSummary()).toBeNull();
  });
});
