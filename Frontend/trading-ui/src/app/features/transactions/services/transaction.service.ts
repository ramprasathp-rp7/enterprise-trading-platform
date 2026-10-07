import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TransactionsService } from '../../../api/generated/transactions';
import {
  TransactionResponse,
  TransactionHistoryEntry,
  TransactionHistoryQuery,
} from '../models/transaction.models';

/**
 * Transaction Service
 * Wrapper around the generated Transactions API client (contracts/transactions-api.yaml).
 * Base URL is configured via provideTransactionsApi in app.config.ts.
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly transactionsApi = inject(TransactionsService);

  /**
   * Deposit funds to account
   * POST /api/v1/accounts/{id}/deposits
   * Returns: 201 Created with TransactionResponse
   * Throws: 422 (invalid amount), 404 (account not found)
   */
  deposit(accountId: number, amount: number): Observable<TransactionResponse> {
    return this.transactionsApi.deposit(accountId, { amount });
  }

  /**
   * Withdraw funds from account
   * POST /api/v1/accounts/{id}/withdrawals
   * Returns: 201 Created with TransactionResponse
   * Throws: 400 (insufficient funds), 422 (invalid amount), 404 (account not found)
   */
  withdraw(accountId: number, amount: number): Observable<TransactionResponse> {
    return this.transactionsApi.withdraw(accountId, { amount });
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
    return this.transactionsApi.getTransaction(accountId, transactionId);
  }

  /**
   * Get transaction history with optional filters
   * GET /api/v1/accounts/{id}/transactions?type=X&from=X&to=X
   * Returns: 200 OK with List<TransactionHistoryEntry> (most recent first)
   * Throws: 404 (account not found)
   */
  getTransactionHistory(
    accountId: number,
    query?: TransactionHistoryQuery
  ): Observable<TransactionHistoryEntry[]> {
    return this.transactionsApi.getTransactionHistory(
      accountId,
      query?.type || undefined,
      query?.from || undefined,
      query?.to || undefined
    );
  }
}
