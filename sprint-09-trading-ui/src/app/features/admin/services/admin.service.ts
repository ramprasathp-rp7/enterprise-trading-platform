import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import {
  TradeVolumeData,
  FillRateData,
  AccountActivityData,
  ExposureData,
  AverageTradeSize,
  AssetFee,
  FeeUpdate,
  AuditLog,
  AccountStatus,
  SystemHealth,
  AdminUser,
  AccountDetails,
  Position,
  Order,
} from '../admin.models';
import { environment } from '../../../core/config/environment';

@Injectable({
  providedIn: 'root',
})
export class AdminService {
  private apiUrl = `${environment.TRADE_API_URL}/admin`;
  private analyticsApiUrl = `${environment.TRADE_API_URL}/analytics`;

  // Observable streams for analytics data
  private tradeVolumeSubject = new BehaviorSubject<TradeVolumeData[]>([]);
  private fillRateSubject = new BehaviorSubject<FillRateData[]>([]);
  private accountActivitySubject = new BehaviorSubject<AccountActivityData[]>([]);
  private exposureSubject = new BehaviorSubject<ExposureData[]>([]);
  private feesSubject = new BehaviorSubject<AssetFee[]>([]);

  tradeVolume$ = this.tradeVolumeSubject.asObservable();
  fillRate$ = this.fillRateSubject.asObservable();
  accountActivity$ = this.accountActivitySubject.asObservable();
  exposure$ = this.exposureSubject.asObservable();
  fees$ = this.feesSubject.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Analytics APIs
   */

  /** Get trade volume by date for dashboard chart */
  getTradeVolume(
    startDate: Date,
    endDate: Date,
    groupBy: 'DAY' | 'WEEK' | 'MONTH' = 'DAY'
  ): Observable<TradeVolumeData[]> {
    const params = new HttpParams()
      .set('startDate', startDate.toISOString())
      .set('endDate', endDate.toISOString())
      .set('groupBy', groupBy);

    return this.http.get<TradeVolumeData[]>(
      `${this.analyticsApiUrl}/trade-volume`,
      { params }
    );
  }

  /** Get fill rate analysis by date */
  getFillRate(
    startDate: Date,
    endDate: Date,
    groupBy: 'DAY' | 'WEEK' | 'MONTH' = 'DAY'
  ): Observable<FillRateData[]> {
    const params = new HttpParams()
      .set('startDate', startDate.toISOString())
      .set('endDate', endDate.toISOString())
      .set('groupBy', groupBy);

    return this.http.get<FillRateData[]>(
      `${this.analyticsApiUrl}/fill-rate`,
      { params }
    );
  }

  /** Get most active accounts */
  getActiveAccounts(limit: number = 10): Observable<AccountActivityData[]> {
    const params = new HttpParams().set('limit', limit.toString());

    return this.http.get<AccountActivityData[]>(
      `${this.analyticsApiUrl}/active-accounts`,
      { params }
    );
  }

  /** Get trade exposure by instrument */
  getExposure(
    assetClass?: string
  ): Observable<ExposureData[]> {
    let params = new HttpParams();
    if (assetClass) {
      params = params.set('assetClass', assetClass);
    }

    return this.http.get<ExposureData[]>(
      `${this.analyticsApiUrl}/exposure`,
      { params }
    );
  }

  /** Get average trade size over time */
  getAverageTradeSize(
    startDate: Date,
    endDate: Date,
    groupBy: 'DAY' | 'WEEK' | 'MONTH' = 'DAY'
  ): Observable<AverageTradeSize[]> {
    const params = new HttpParams()
      .set('startDate', startDate.toISOString())
      .set('endDate', endDate.toISOString())
      .set('groupBy', groupBy);

    return this.http.get<AverageTradeSize[]>(
      `${this.analyticsApiUrl}/average-trade-size`,
      { params }
    );
  }

  /**
   * Fee Management APIs
   */

  /** Get all asset fees */
  getAssetFees(): Observable<AssetFee[]> {
    return this.http.get<AssetFee[]>(
      `${environment.TRADE_API_URL}/api/v1/admin/fees`
    );
  }

  /** Get fee for a specific asset type */
  getAssetFee(assetType: string): Observable<AssetFee> {
    return this.http.get<AssetFee>(
      `${environment.TRADE_API_URL}/api/v1/admin/fees/${assetType}`
    );
  }

  /** Update fee for an asset class */
  updateAssetFee(feeUpdate: FeeUpdate): Observable<AssetFee> {
    return this.http.put<AssetFee>(
      `${this.apiUrl}/fees/${feeUpdate.assetType}`,
      feeUpdate
    );
  }

  /** Get fee history for audit trail */
  getFeeHistory(assetType: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/fees/${assetType}/history`);
  }

  /**
   * Account Management APIs
   */

  /** Get account details by account ID */
  getAccountDetails(accountId: number): Observable<AccountDetails> {
    return this.http.get<AccountDetails>(
      `${environment.TRADE_API_URL}/api/v1/accounts/${accountId}`
    );
  }

  /** Get positions for a specific account */
  getAccountPositions(accountId: number): Observable<Position[]> {
    return this.http.get<Position[]>(
      `${environment.TRADE_API_URL}/api/v1/accounts/${accountId}/positions`
    );
  }

  /** Get order history for a specific account */
  getAccountOrders(accountId: number): Observable<Order[]> {
    return this.http.get<Order[]>(
      `${environment.TRADE_API_URL}/api/v1/accounts/${accountId}/orders`
    );
  }

  /** Update account status */
  updateAccountStatus(
    accountId: string,
    status: AccountStatus
  ): Observable<void> {
    return this.http.put<void>(
      `${this.apiUrl}/accounts/${accountId}/status`,
      status
    );
  }

  /** Get account status history */
  getAccountStatusHistory(accountId: string): Observable<AccountStatus[]> {
    return this.http.get<AccountStatus[]>(
      `${this.apiUrl}/accounts/${accountId}/status-history`
    );
  }

  /** Get all admin users/accounts */
  getAdminUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${this.apiUrl}/users`);
  }

  /**
   * System Administration APIs
   */

  /** Get system health status */
  getSystemHealth(): Observable<SystemHealth> {
    return this.http.get<SystemHealth>(`${this.apiUrl}/health`);
  }

  /** Get audit logs */
  getAuditLogs(
    entity?: string,
    startDate?: Date,
    endDate?: Date,
    limit: number = 100
  ): Observable<AuditLog[]> {
    let params = new HttpParams().set('limit', limit.toString());

    if (entity) {
      params = params.set('entity', entity);
    }
    if (startDate) {
      params = params.set('startDate', startDate.toISOString());
    }
    if (endDate) {
      params = params.set('endDate', endDate.toISOString());
    }

    return this.http.get<AuditLog[]>(`${this.apiUrl}/audit-logs`, { params });
  }

  /**
   * Trigger ETL refresh */
  triggerEtlRefresh(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/etl/refresh`, {});
  }
}
