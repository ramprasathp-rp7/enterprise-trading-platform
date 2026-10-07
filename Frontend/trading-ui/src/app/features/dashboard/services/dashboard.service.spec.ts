import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DashboardService } from './dashboard.service';
import { AccountsService } from '../../../api/generated/trade';
import { AccountHistoryService } from '../../../api/generated/account-history';

describe('DashboardService', () => {
  let service: DashboardService;
  let accounts: { getBalance: ReturnType<typeof vi.fn>; getAccount: ReturnType<typeof vi.fn> };
  let accountHistory: { getAccountHistory: ReturnType<typeof vi.fn> };

  const balance = { accountId: 7, cashBalance: 1000, currency: 'USD', asOf: '2026-10-05T08:00:00Z' };
  const account = { id: 1, accountId: 'ACC-000007', holderName: 'Rahul', cashBalance: 1000, status: 'ACTIVE', version: 1, lastUpdated: '' };

  beforeEach(() => {
    accounts = { getBalance: vi.fn(() => of(balance)), getAccount: vi.fn(() => of(account)) };
    accountHistory = { getAccountHistory: vi.fn(() => of([])) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AccountsService, useValue: accounts },
        { provide: AccountHistoryService, useValue: accountHistory },
      ],
    });
    service = TestBed.inject(DashboardService);
  });

  it('delegates balance and account lookups to the accounts API', () => {
    let gotBalance: unknown;
    let gotAccount: unknown;

    service.getBalance(7).subscribe((b) => (gotBalance = b));
    service.getAccount(7).subscribe((a) => (gotAccount = a));

    expect(accounts.getBalance).toHaveBeenCalledWith(7);
    expect(accounts.getAccount).toHaveBeenCalledWith(7);
    expect(gotBalance).toEqual(balance);
    expect(gotAccount).toEqual(account);
  });

  it('maps net worth snapshots to daily points, oldest first', () => {
    accountHistory.getAccountHistory.mockReturnValue(
      of([
        { accountId: 7, snapshotDate: '2026-10-05T23:59:59Z', cashBalance: 500, networth: 1250.5 },
        { accountId: 7, snapshotDate: '2026-10-04T23:59:59Z', cashBalance: 500, networth: 1200 },
        { accountId: 7, snapshotDate: 'garbage', cashBalance: 0, networth: 1 },
      ]),
    );
    let points: unknown;

    service.getNetWorthHistory(7).subscribe((p) => (points = p));

    expect(accountHistory.getAccountHistory).toHaveBeenCalledWith(7, 90);
    expect(points).toEqual([
      { date: new Date('2026-10-04T00:00:00Z'), value: 1200 },
      { date: new Date('2026-10-05T00:00:00Z'), value: 1250.5 },
    ]);
  });

  it('combines account and balance into one dashboard payload', () => {
    const next = vi.fn();
    const complete = vi.fn();

    service.getPortfolioDashboard(7).subscribe({ next, complete });

    expect(next).toHaveBeenCalledWith({ account, balance });
    expect(complete).toHaveBeenCalled();
  });

  it('propagates an account lookup failure', () => {
    accounts.getAccount.mockReturnValue(throwError(() => new Error('no account')));
    const error = vi.fn();

    service.getPortfolioDashboard(7).subscribe({ error });

    expect(error).toHaveBeenCalledWith(new Error('no account'));
    expect(accounts.getBalance).not.toHaveBeenCalled();
  });

  it('propagates a balance lookup failure', () => {
    accounts.getBalance.mockReturnValue(throwError(() => new Error('no balance')));
    const error = vi.fn();

    service.getPortfolioDashboard(7).subscribe({ error });

    expect(error).toHaveBeenCalledWith(new Error('no balance'));
  });

  it('throws for the endpoints that are not implemented yet', () => {
    expect(() => service.getPositions()).toThrow('Not implemented');
    expect(() => service.getPortfolioPerformance()).toThrow('Not implemented');
    expect(() => service.refreshDashboard()).toThrow('Not implemented');
  });
});
