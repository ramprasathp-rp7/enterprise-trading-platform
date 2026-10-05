import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminAnalyticsComponent } from './admin-analytics.component';
import { AdminService } from '../../services/admin.service';

describe('AdminAnalyticsComponent', () => {
  let fixture: ComponentFixture<AdminAnalyticsComponent>;
  let component: AdminAnalyticsComponent;
  let admin: { getCompleteOrderHistory: ReturnType<typeof vi.fn>; getCompleteTransactionHistory: ReturnType<typeof vi.fn> };

  // Two orders: o1 created then filled, o2 created then rejected
  const orderHistory = [
    { orderId: 'o1', timestamp: '2026-10-01T10:00:00Z', fromStatus: null, toStatus: 'NEW' },
    { orderId: 'o1', timestamp: '2026-10-01T10:00:01Z', fromStatus: 'NEW', toStatus: 'FILLED' },
    { orderId: 'o2', timestamp: '2026-10-01T11:00:00Z', fromStatus: null, toStatus: 'NEW' },
    { orderId: 'o2', timestamp: '2026-10-01T11:00:01Z', fromStatus: 'NEW', toStatus: 'REJECTED' },
  ];
  const transactions = [
    { transactionId: 't1', transactionType: 'BUY', actionType: 'DEBIT', amount: 1000, createdOn: '' },
    { transactionId: 't2', transactionType: 'SELL', actionType: 'CREDIT', amount: '500.5', createdOn: '' },
    { transactionId: 't3', transactionType: 'FEE', actionType: 'DEBIT', amount: 4.95, createdOn: '' },
    { transactionId: 't4', transactionType: 'DEPOSIT', actionType: 'CREDIT', amount: 9999, createdOn: '' },
  ];

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    admin = {
      getCompleteOrderHistory: vi.fn(() => of(orderHistory)),
      getCompleteTransactionHistory: vi.fn(() => of(transactions)),
    };
    await TestBed.configureTestingModule({
      imports: [AdminAnalyticsComponent],
      providers: [{ provide: AdminService, useValue: admin }],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminAnalyticsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads the last 30 days by default and derives the KPIs', async () => {
    await settle();

    const [from, to] = admin.getCompleteOrderHistory.mock.calls[0];
    const days = (new Date(to).getTime() - new Date(from).getTime()) / 86_400_000;
    expect(days).toBeGreaterThanOrEqual(28);
    expect(days).toBeLessThanOrEqual(31);

    expect(component.totalOrders()).toBe(2);
    expect(component.totalTradeValue()).toBeCloseTo(1500.5);
    expect(component.totalFeeValue()).toBeCloseTo(4.95);
    expect(component.fillRate()).toBe(50);
    expect(component.isLoading()).toBe(false);
    expect(fixture.nativeElement.querySelectorAll('.metric-card').length).toBe(4);
    expect(fixture.nativeElement.textContent).toContain('$1,500.50');
    expect(fixture.nativeElement.textContent).toContain('50.00%');
  });

  it('requests 7 and 90 day windows when the range changes', async () => {
    await settle();

    component.dateRange.set('7d');
    await settle();
    let [from, to] = admin.getCompleteOrderHistory.mock.calls.at(-1)!;
    expect(Math.round((new Date(to).getTime() - new Date(from).getTime()) / 86_400_000)).toBe(7);

    component.dateRange.set('90d');
    await settle();
    [from, to] = admin.getCompleteOrderHistory.mock.calls.at(-1)!;
    const days = (new Date(to).getTime() - new Date(from).getTime()) / 86_400_000;
    expect(days).toBeGreaterThanOrEqual(88);
    expect(days).toBeLessThanOrEqual(93);
  });

  it('requests all history without dates for all-time', async () => {
    await settle();

    component.dateRange.set('all-time');
    await settle();

    expect(admin.getCompleteOrderHistory).toHaveBeenLastCalledWith(undefined, undefined);
    expect(admin.getCompleteTransactionHistory).toHaveBeenLastCalledWith(undefined, undefined);
  });

  it('retries transactions without a date filter when the range has none', async () => {
    const all = [{ transactionId: 'x', transactionType: 'FEE', actionType: 'DEBIT', amount: 2, createdOn: '' }];
    admin.getCompleteTransactionHistory.mockReturnValueOnce(of([])).mockReturnValueOnce(of(all));

    await settle();

    expect(admin.getCompleteTransactionHistory).toHaveBeenCalledTimes(2);
    expect(admin.getCompleteTransactionHistory.mock.calls[1]).toEqual([]);
    expect(component.totalFeeValue()).toBe(2);
  });

  it('treats null responses as empty history', async () => {
    admin.getCompleteOrderHistory.mockReturnValue(of(null));
    admin.getCompleteTransactionHistory.mockReturnValue(of(null));

    await settle();

    expect(component.totalOrders()).toBe(0);
    expect(component.fillRate()).toBe(0);
    expect(component.totalTradeValue()).toBe(0);
  });

  it('shows an error when loading fails', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    admin.getCompleteOrderHistory.mockReturnValue(throwError(() => new Error('down')));

    await settle();

    expect(component.errorMessage()).toBe('Failed to load analytics data. Please try again.');
    expect(component.isLoading()).toBe(false);
    expect(fixture.nativeElement.querySelector('.alert-error').textContent).toContain('Failed to load analytics data');
  });

  it('formats currency and percentages', () => {
    expect(component.formatCurrency(1234.5)).toBe('$1,234.50');
    expect(component.formatPercent(87.5)).toBe('87.50%');
  });
});
