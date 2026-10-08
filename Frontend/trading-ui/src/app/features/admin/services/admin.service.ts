import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminAuditComplianceService,
  AdminConfigurationService,
  OrderStatusChange,
  TransactionHistoryEntry,
  FeeStructureResponse,
  FeeUpdateRequest,
} from '../../../api/generated/admin';

/**
 * Admin Service
 * Wrapper around generated admin API clients for convenience.
 * Provides typed access to system-wide auditing, compliance, and configuration endpoints.
 */
@Injectable({
  providedIn: 'root',
})
export class AdminService {
  constructor(
    private auditComplianceService: AdminAuditComplianceService,
    private configurationService: AdminConfigurationService
  ) {}

  /**
   * Audit & Compliance APIs
   */

  /**
   * Get complete order history across all accounts
   * Returns all order status changes (creation → transitions → final state) sorted newest-first.
   * 
   * @param from optional start date (ISO 8601 format)
   * @param to optional end date (ISO 8601 format)
   * @returns list of order status changes across all accounts
   */
  getCompleteOrderHistory(
    from?: string,
    to?: string
  ): Observable<OrderStatusChange[]> {
    return this.auditComplianceService.getCompleteOrderHistory(from, to);
  }

  /**
   * Get complete transaction history across all accounts
   * Returns all transactions (deposits, withdrawals, fees, settlements) sorted newest-first.
   * 
   * @param from optional start date (ISO 8601 format)
   * @param to optional end date (ISO 8601 format)
   * @returns list of all transactions across all accounts
   */
  getCompleteTransactionHistory(
    from?: string,
    to?: string
  ): Observable<TransactionHistoryEntry[]> {
    return this.auditComplianceService.getCompleteTransactionHistory(from, to);
  }

  /**
   * Configuration APIs
   */

  /**
   * Get all asset fee structures
   * Returns fee configuration for all asset classes.
   * 
   * @returns list of all fee structures
   */
  getAssetFees(): Observable<FeeStructureResponse[]> {
    return this.configurationService.getAllFees();
  }

  /**
   * Get fee structure for a specific asset type
   * Returns fee configuration for a specific asset class.
   * 
   * @param assetType the asset type (e.g., EQUITY, FOREX)
   * @returns the fee structure for the asset type
   */
  getAssetFee(assetType: string): Observable<FeeStructureResponse> {
    return this.configurationService.getFeeByAssetType(assetType);
  }

  /**
   * Update the fee structure for an asset type
   * Takes effect for orders placed after the update.
   *
   * @param assetType the asset type (e.g., EQUITY, FX)
   * @param fees the new flat fee and percentage fee (decimal form, 0.001 = 0.1%)
   * @returns the fee structure as now stored
   */
  updateAssetFee(assetType: string, fees: FeeUpdateRequest): Observable<FeeStructureResponse> {
    return this.configurationService.updateFee(assetType, fees);
  }
}
