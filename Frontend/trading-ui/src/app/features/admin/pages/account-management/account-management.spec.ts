import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminAccountManagementComponent } from './account-management';
import { AdminService } from '../../services/admin.service';
import { AccountsService } from '../../../../api/generated/trade';

describe('AdminAccountManagementComponent', () => {
  let component: AdminAccountManagementComponent;
  let fixture: ComponentFixture<AdminAccountManagementComponent>;
  let accounts: {
    getAccount: ReturnType<typeof vi.fn>;
    getPositions: ReturnType<typeof vi.fn>;
    getOrders: ReturnType<typeof vi.fn>;
  };

  const account = {
    id: 7,
    accountId: 'ACC-001001',
    holderName: 'Rahul Kumar',
    cashBalance: '48250.75',
    status: 'ACTIVE',
    version: 12,
    lastUpdated: '2026-10-05T08:12:00Z',
  };
  const positions = [
    { accountId: 1001, symbol: 'AAPL', quantity: '420', averageCost: '182.40' },
    { accountId: 1001, symbol: 'MSFT', quantity: 260, averageCost: 351.1 },
  ];
  const orders = [
    { orderId: 'o1', accountId: 1001, symbol: 'AAPL', side: 'BUY', quantity: 100, price: 189.35, executedPrice: 189.3, status: 'FILLED', idempotencyKey: 'k1', createdOn: '2026-10-05T09:30:00Z' },
    { orderId: 'o2', accountId: 1001, symbol: 'MSFT', side: 'SELL', quantity: 25, price: 352.1, executedPrice: null, status: 'NEW', createdOn: '2026-10-05T09:23:00Z' },
  ];

  async function settle(): Promise<void> {
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
    accounts = {
      getAccount: vi.fn(() => of(account)),
      getPositions: vi.fn(() => of(positions)),
      getOrders: vi.fn(() => of(orders)),
    };

    await TestBed.configureTestingModule({
      imports: [AdminAccountManagementComponent],
      providers: [
        { provide: AccountsService, useValue: accounts },
        { provide: AdminService, useValue: {} },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAccountManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  it('should create with the initial empty state', () => {
    expect(component).toBeTruthy();
    expect(component.activeTab()).toBe('positions');
    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('Enter an account ID to search');
  });

  it('rejects an empty, zero or non-numeric account id without calling the API', () => {
    for (const value of ['', '0', '-3', 'abc']) {
      component.searchAccountId.set(value);
      component.lookupAccount();
      expect(component.errorMessage()).toBe('Please enter a valid account ID');
    }
    expect(accounts.getAccount).not.toHaveBeenCalled();
  });

  it('loads and maps account, positions and orders', async () => {
    component.searchAccountId.set('1001');

    component.lookupAccount();
    expect(component.loading()).toBe(true);
    await settle();

    expect(accounts.getAccount).toHaveBeenCalledWith(1001);
    expect(accounts.getPositions).toHaveBeenCalledWith(1001);
    expect(accounts.getOrders).toHaveBeenCalledWith(1001);
    expect(component.loading()).toBe(false);
    expect(component.successMessage()).toBe('Account data loaded successfully');

    const details = component.accountDetails()!;
    expect(details.holderName).toBe('Rahul Kumar');
    expect(details.cashBalance).toBe(48250.75);
    expect(details.lastUpdated).toBeInstanceOf(Date);

    expect(component.positions()[0]).toEqual({ accountId: 1001, symbol: 'AAPL', quantity: 420, averageCost: 182.4 });
    expect(component.orders()[0].executedPrice).toBe(189.3);
    expect(component.orders()[1].executedPrice).toBeUndefined();
    expect(component.orders()[1].idempotencyKey).toBe('');

    expect(fixture.nativeElement.querySelector('.account-name').textContent).toContain('Rahul Kumar');
    expect(fixture.nativeElement.querySelectorAll('.positions-table tbody tr').length).toBe(2);
  });

  it('switches between the positions and orders tabs', async () => {
    component.searchAccountId.set('1001');
    component.lookupAccount();
    await settle();

    const tabs = fixture.nativeElement.querySelectorAll('.tab-button');
    tabs[1].click();
    fixture.detectChanges();

    expect(component.activeTab()).toBe('orders');
    expect(fixture.nativeElement.querySelectorAll('.orders-table tbody tr').length).toBe(2);

    component.setActiveTab('positions');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.orders-table')).toBeNull();
  });

  it('shows empty tabs when the account has no positions or orders', async () => {
    accounts.getPositions.mockReturnValue(of(null));
    accounts.getOrders.mockReturnValue(of([]));
    component.searchAccountId.set('1001');

    component.lookupAccount();
    await settle();

    expect(component.positions()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.tab-content .empty-state').textContent).toContain('No positions found');
  });

  it('shows an error when any request fails', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    accounts.getOrders.mockReturnValue(throwError(() => new Error('down')));
    component.searchAccountId.set('1001');

    component.lookupAccount();
    await settle();

    expect(component.loading()).toBe(false);
    expect(component.errorMessage()).toBe('Failed to load account data. Please try again.');
    expect(component.accountDetails()).toBeNull();
  });

  it('formats currency, amounts and dates', () => {
    expect(component.formatCurrency(48250.75)).toBe('$48,250.75');
    expect(component.formatAmount(1489.5)).toBe('1,489.50');
    expect(component.formatDate(null)).toBe('');
    expect(component.formatDate(undefined)).toBe('');
    expect(component.formatDate('  ')).toBe('');
    expect(component.formatDate('not-a-date')).toBe('not-a-date');
    expect(component.formatDate('2026-10-05T08:12:00Z')).toContain('2026');
    expect(component.formatDate(1790000000)).toBe(component.formatDate(1790000000000));
  });
});
