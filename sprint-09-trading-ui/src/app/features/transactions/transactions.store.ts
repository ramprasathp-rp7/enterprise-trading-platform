import { inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { patchState, signalStore, withState, withComputed, withMethods } from '@ngrx/signals';
import { computed } from '@angular/core';
import { TransactionService } from './services/transaction.service';
import {
  TransactionResponse,
  TransactionHistoryEntry,
  TransactionHistoryQuery,
  ErrorResponse,
} from './models/transaction.models';

interface TransactionsState {
  transactions: TransactionHistoryEntry[];
  loading: boolean;
  error: ErrorResponse | null;
  lastAction: 'load' | 'deposit' | 'withdraw' | null;
  lastActionSuccess: boolean;
}

const initialState: TransactionsState = {
  transactions: [],
  loading: false,
  error: null,
  lastAction: null,
  lastActionSuccess: false,
};

/**
 * Transactions Store
 * Manages transaction state using NgRx Signals
 */
export const TransactionsStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed(({ transactions, error }) => ({
    transactionCount: computed(() => transactions().length),
    hasError: computed(() => error() !== null),
    totalDeposits: computed(() =>
      transactions()
        .filter((t) => t.actionType === 'CREDIT')
        .reduce((sum, t) => sum + t.amount, 0)
    ),
    totalWithdrawals: computed(() =>
      transactions()
        .filter((t) => t.actionType === 'DEBIT')
        .reduce((sum, t) => sum + t.amount, 0)
    ),
  })),
  withMethods((store, transactionService = inject(TransactionService)) => ({
    loadTransactions(accountId: number, query?: TransactionHistoryQuery) {
      patchState(store, { loading: true, error: null });
      transactionService.getTransactionHistory(accountId, query).subscribe({
        next: (transactions: TransactionHistoryEntry[]) => {
          patchState(store, {
            transactions,
            loading: false,
            lastAction: 'load',
            lastActionSuccess: true,
          });
        },
        error: (err: HttpErrorResponse) => {
          const error: ErrorResponse = err.error || {
            errorCode: 'UNKNOWN_ERROR',
            message: 'Failed to load transactions',
          };
          patchState(store, {
            error,
            loading: false,
            lastActionSuccess: false,
          });
        },
      });
    },

    deposit(accountId: number, amount: number) {
      patchState(store, { loading: true, error: null, lastAction: 'deposit' });
      transactionService.deposit(accountId, amount).subscribe({
        next: (response: TransactionResponse) => {
          const entry: TransactionHistoryEntry = {
            transactionId: response.transactionId,
            transactionType: response.transactionType,
            actionType: response.actionType,
            amount: response.amount,
            createdOn: response.createdOn,
          };
          patchState(store, {
            transactions: [entry, ...store.transactions()],
            loading: false,
            lastActionSuccess: true,
          });
        },
        error: (err: HttpErrorResponse) => {
          const error: ErrorResponse = err.error || {
            errorCode: 'DEPOSIT_FAILED',
            message: 'Deposit failed',
          };
          patchState(store, {
            error,
            loading: false,
            lastActionSuccess: false,
          });
        },
      });
    },

    withdraw(accountId: number, amount: number) {
      patchState(store, { loading: true, error: null, lastAction: 'withdraw' });
      transactionService.withdraw(accountId, amount).subscribe({
        next: (response: TransactionResponse) => {
          const entry: TransactionHistoryEntry = {
            transactionId: response.transactionId,
            transactionType: response.transactionType,
            actionType: response.actionType,
            amount: response.amount,
            createdOn: response.createdOn,
          };
          patchState(store, {
            transactions: [entry, ...store.transactions()],
            loading: false,
            lastActionSuccess: true,
          });
        },
        error: (err: HttpErrorResponse) => {
          const error: ErrorResponse = err.error || {
            errorCode: 'WITHDRAW_FAILED',
            message: 'Withdrawal failed',
          };
          patchState(store, {
            error,
            loading: false,
            lastActionSuccess: false,
          });
        },
      });
    },

    clearError() {
      patchState(store, { error: null });
    },
  }))
);
