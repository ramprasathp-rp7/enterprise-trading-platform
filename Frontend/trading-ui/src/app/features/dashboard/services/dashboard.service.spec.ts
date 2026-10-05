import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DashboardService } from './dashboard.service';
import { AccountsService } from '../../../api/generated/trade';

describe('DashboardService', () => {
  let service: DashboardService;
  let accounts: { getBalance: ReturnType<typeof vi.fn>; getAccount: ReturnType<typeof vi.fn> };

  const balance = { accountId: 7, cashBalance: 1000, currency: 'USD', asOf: '2026-10-05T08:00:00Z' };
  const account = { id: 1, accountId: 'ACC-000007', holderName: 'Rahul', cashBalance: 1000, status: 'ACTIVE', version: 1, lastUpdated: '' };

  beforeEach(() => {
    accounts = { getBalance: vi.fn(() => of(balance)), getAccount: vi.fn(() => of(account)) };
    TestBed.configureTestingModule({ providers: [{ provide: AccountsService, useValue: accounts }] });
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
