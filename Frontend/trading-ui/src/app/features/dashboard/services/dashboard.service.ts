import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { AccountsService, BalanceResponse, AccountResponse } from '../../../api/generated/trade';
import { AccountHistoryService } from '../../../api/generated/account-history';

/** One end-of-day net worth reading; `date` is the UTC midnight of the snapshot's day */
export interface NetWorthPoint {
  date: Date;
  value: number;
}

/** Days of history the dashboard chart needs: its longest range is 3 months */
export const NET_WORTH_HISTORY_DAYS = 90;

/**
 * Dashboard Service
 * Handles API calls for dashboard data
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private accountsService = inject(AccountsService);
  private accountHistoryService = inject(AccountHistoryService);

  /**
   * Get account balance information
   */
  getBalance(accountId: number): Observable<BalanceResponse> {
    return this.accountsService.getBalance(accountId);
  }

  /**
   * Get account details
   */
  getAccount(accountId: number): Observable<AccountResponse> {
    return this.accountsService.getAccount(accountId);
  }

  /**
   * Daily net worth snapshots for the past `days` days, oldest first.
   * The API returns them newest first, stamped at the end of each UTC day.
   */
  getNetWorthHistory(accountId: number, days: number = NET_WORTH_HISTORY_DAYS): Observable<NetWorthPoint[]> {
    return this.accountHistoryService.getAccountHistory(accountId, days).pipe(
      map((snapshots) =>
        (snapshots ?? [])
          .map((s) => ({ date: utcDay(s.snapshotDate), value: Number(s.networth) }))
          .filter((p) => !Number.isNaN(p.date.getTime()) && Number.isFinite(p.value))
          .sort((a, b) => a.date.getTime() - b.date.getTime()),
      ),
    );
  }

  /**
   * Get portfolio dashboard data
   */
  getPortfolioDashboard(accountId: number): Observable<any> {
    // Combine balance and account info
    return new Observable(observer => {
      this.getAccount(accountId).subscribe({
        next: (account) => {
          this.getBalance(accountId).subscribe({
            next: (balance) => {
              observer.next({
                account,
                balance
              });
              observer.complete();
            },
            error: (err) => observer.error(err)
          });
        },
        error: (err) => observer.error(err)
      });
    });
  }

  /**
   * Get positions
   */
  getPositions(): Observable<any> {
    throw new Error('Not implemented');
  }

  /**
   * Get portfolio performance
   */
  getPortfolioPerformance(days: number = 30): Observable<any> {
    throw new Error('Not implemented');
  }

  /**
   * Refresh dashboard
   */
  refreshDashboard(): Observable<any> {
    throw new Error('Not implemented');
  }
}

/** UTC midnight of the snapshot's day ("2026-10-06T23:59:59Z" -> 2026-10-06T00:00:00Z) */
function utcDay(snapshotDate: string): Date {
  return new Date(`${snapshotDate.slice(0, 10)}T00:00:00Z`);
}
