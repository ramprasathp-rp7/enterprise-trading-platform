import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  TransactionRequest,
  TransactionResponse,
  TransactionHistoryEntry,
  TransactionHistoryQuery,
} from '../models/transaction.models';

/**
 * Transaction API Service
 * Handles all HTTP communication with Sprint 07 Transaction endpoints
 * Base URL: http://localhost:8080/api/v1/accounts
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = 'http://localhost:8080/api/v1/accounts';

  /**
   * Deposit funds to account
   * POST /api/v1/accounts/{id}/deposits
   * Returns: 201 Created with TransactionResponse
   * Throws: 400 (invalid amount), 404 (account not found)
   */
  deposit(accountId: number, amount: number): Observable<TransactionResponse> {
    const request: TransactionRequest = { amount };
    return this.http.post<TransactionResponse>(
      `${this.apiBaseUrl}/${accountId}/deposits`,
      request
    );
  }

  /**
   * Withdraw funds from account
   * POST /api/v1/accounts/{id}/withdrawals
   * Returns: 201 Created with TransactionResponse
   * Throws: 400 (invalid amount, insufficient funds), 404 (account not found)
   */
  withdraw(accountId: number, amount: number): Observable<TransactionResponse> {
    const request: TransactionRequest = { amount };
    return this.http.post<TransactionResponse>(
      `${this.apiBaseUrl}/${accountId}/withdrawals`,
      request
    );
  }

  /**
   * Get specific transaction by ID
   * GET /api/v1/accounts/{id}/transactions/{transactionId}
   * Returns: 200 OK with TransactionResponse
   * Throws: 404 (transaction or account not found)
   */
  getTransaction(
    accountId: number,
    transactionId: string
  ): Observable<TransactionResponse> {
    return this.http.get<TransactionResponse>(
      `${this.apiBaseUrl}/${accountId}/transactions/${transactionId}`
    );
  }

  /**
   * Get transaction history with optional filters
   * GET /api/v1/accounts/{id}/transactions?type=X&from=X&to=X
   * Query Parameters:
   *   - type (optional): DEPOSIT | WITHDRAW | FEE | BUY | SELL
   *   - from (optional): ISO 8601 date-time (e.g., 2026-01-01T00:00:00Z)
   *   - to (optional): ISO 8601 date-time (e.g., 2026-12-31T23:59:59Z)
   * Returns: 200 OK with List<TransactionHistoryEntry> (most recent first)
   * Throws: 404 (account not found)
   */
  getTransactionHistory(
    accountId: number,
    query?: TransactionHistoryQuery
  ): Observable<TransactionHistoryEntry[]> {
    let params = new HttpParams();

    if (query?.type) {
      params = params.set('type', query.type);
    }
    if (query?.from) {
      params = params.set('from', query.from);
    }
    if (query?.to) {
      params = params.set('to', query.to);
    }

    return this.http.get<TransactionHistoryEntry[]>(
      `${this.apiBaseUrl}/${accountId}/transactions`,
      { params }
    );
  }
}
