import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AccountsService, BalanceResponse, AccountResponse } from '../../../api/generated/trade';

/**
 * Dashboard Service
 * Handles API calls for dashboard data
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private accountsService = inject(AccountsService);

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
