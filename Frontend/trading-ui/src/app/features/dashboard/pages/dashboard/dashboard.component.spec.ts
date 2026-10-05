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

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let dashboardService: { getBalance: ReturnType<typeof vi.fn>; getAccount: ReturnType<typeof vi.fn> };
  let blotterStore: { orders: ReturnType<typeof signal<any[]>>; loadOrderHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

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
    };
    blotterStore = { orders: signal<any[]>(orders), loadOrderHistory: vi.fn() };
    currentUser = vi.fn(() => ({ accountId: 7 }));
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

  it('shows the cash balance in the portfolio and cash KPI cards', async () => {
    await create();

    const stats = component.portfolioStats();
    expect(stats.map((s) => s.title)).toEqual(['Total Portfolio Value', "Today's Profit/Loss", 'Available Cash']);
    expect(stats[0].value).toBe('48,250.75');
    expect(stats[2].value).toBe('48,250.75');
    expect(fixture.nativeElement.querySelectorAll('.metric-card').length).toBe(3);
  });

  it('maps the three most recent orders to trades with display statuses', async () => {
    await create();

    const trades = component.recentTrades();
    expect(trades.map((t) => t.id)).toEqual(['o1', 'o2', 'o3']);
    expect(trades.map((t) => t.status)).toEqual(['EXECUTED', 'PENDING', 'REJECTED']);
    expect(trades[0].time).toContain('2026');
    expect(trades[2].time).toBe('not-a-date');
    expect(fixture.nativeElement.querySelectorAll('tr.trade-row').length).toBe(3);
  });

  it('maps CANCELLED and unknown statuses and handles empty timestamps', async () => {
    blotterStore.orders.set([orders[3], { ...orders[0], orderId: 'o5', status: 'MYSTERY', createdOn: '  ' }]);
    await create();

    const trades = component.recentTrades();
    expect(trades.map((t) => t.status)).toEqual(['CANCELLED', 'PENDING']);
    expect(trades.map((t) => t.time)).toEqual(['', '']);
  });

  it('renders the empty state when there are no orders', async () => {
    blotterStore.orders.set([]);
    await create();

    expect(component.recentTrades()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No trades yet');
  });

  it('skips all loading without a signed-in account', async () => {
    currentUser.mockReturnValue(null);
    await create();

    expect(blotterStore.loadOrderHistory).not.toHaveBeenCalled();
    expect(dashboardService.getBalance).not.toHaveBeenCalled();
    expect(component.portfolioStats()[0].value).toBe('0.00');
    expect(component.accountDisplayName()).toBeNull();
  });

  it('logs and keeps defaults when balance or account requests fail', async () => {
    dashboardService.getBalance.mockReturnValue(throwError(() => new Error('down')));
    dashboardService.getAccount.mockReturnValue(throwError(() => new Error('down')));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await create();

    expect(consoleError).toHaveBeenCalledTimes(2);
    expect(component.portfolioStats()[2].value).toBe('0.00');
    expect(component.accountDisplayId()).toBeNull();
    consoleError.mockRestore();
  });

  it('refreshDashboard reloads orders and balance', async () => {
    await create();
    dashboardService.getBalance.mockReturnValue(of({ accountId: 7, cashBalance: 100, currency: 'USD', asOf: '' }));

    component.refreshDashboard();

    expect(blotterStore.loadOrderHistory).toHaveBeenCalledTimes(2);
    expect(component.portfolioStats()[2].value).toBe('100.00');
  });

  it('refreshDashboard does nothing without an account', async () => {
    await create();
    currentUser.mockReturnValue(null);

    component.refreshDashboard();

    expect(blotterStore.loadOrderHistory).toHaveBeenCalledTimes(1);
  });

  it('maps statuses to classes and icons', async () => {
    await create();

    expect(component.getStatusClass('EXECUTED')).toBe('status-executed');
    expect(component.getStatusIcon('executed')).toBe('fa-check-circle');
    expect(component.getStatusIcon('PENDING')).toBe('fa-hourglass-half');
    expect(component.getStatusIcon('CANCELLED')).toBe('fa-times-circle');
    expect(component.getStatusIcon('REJECTED')).toBe('fa-ban');
    expect(component.getStatusIcon('OTHER')).toBe('fa-circle');
  });

  it('shows a rejected order with the red REJECTED badge, not CANCELLED', async () => {
    blotterStore.orders.set([orders[2]]);
    await create();

    const pill = fixture.nativeElement.querySelector('tr.trade-row .pill');
    expect(pill.textContent.trim()).toBe('REJECTED');
    expect(pill.classList).toContain('status-rejected');
  });
});
