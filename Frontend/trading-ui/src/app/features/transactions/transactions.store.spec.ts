import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TransactionsStore } from './transactions.store';
import { TransactionService } from './services/transaction.service';
import { TransactionHistoryEntry, TransactionResponse } from './models/transaction.models';

describe('TransactionsStore', () => {
  let store: InstanceType<typeof TransactionsStore>;
  let service: {
    getTransactionHistory: ReturnType<typeof vi.fn>;
    deposit: ReturnType<typeof vi.fn>;
    withdraw: ReturnType<typeof vi.fn>;
  };

  const history: TransactionHistoryEntry[] = [
    { transactionId: 't1', transactionType: 'DEPOSIT', actionType: 'CREDIT', amount: 500, createdOn: '2026-10-01T10:00:00Z' },
    { transactionId: 't2', transactionType: 'WITHDRAW', actionType: 'DEBIT', amount: 120, createdOn: '2026-10-02T10:00:00Z' },
    { transactionId: 't3', transactionType: 'FEE', actionType: 'DEBIT', amount: 5, createdOn: '2026-10-03T10:00:00Z' },
  ];

  const response = (overrides: Partial<TransactionResponse>): TransactionResponse => ({
    transactionId: 'new-1',
    accountId: 7,
    transactionType: 'DEPOSIT',
    actionType: 'CREDIT',
    amount: 100,
    createdOn: '2026-10-05T10:00:00Z',
    ...overrides,
  });

  beforeEach(() => {
    service = { getTransactionHistory: vi.fn(), deposit: vi.fn(), withdraw: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: TransactionService, useValue: service }],
    });
    store = TestBed.inject(TransactionsStore);
  });

  it('starts empty with no error', () => {
    expect(store.transactions()).toEqual([]);
    expect(store.loading()).toBe(false);
    expect(store.hasError()).toBe(false);
    expect(store.transactionCount()).toBe(0);
  });

  describe('loadTransactions', () => {
    it('stores history and derives totals', () => {
      service.getTransactionHistory.mockReturnValue(of(history));

      store.loadTransactions(7, { type: 'DEPOSIT' });

      expect(service.getTransactionHistory).toHaveBeenCalledWith(7, { type: 'DEPOSIT' });
      expect(store.transactions()).toEqual(history);
      expect(store.transactionCount()).toBe(3);
      expect(store.totalDeposits()).toBe(500);
      expect(store.totalWithdrawals()).toBe(125);
      expect(store.lastAction()).toBe('load');
      expect(store.lastActionSuccess()).toBe(true);
      expect(store.loading()).toBe(false);
    });

    it('keeps the server error body when loading fails', () => {
      service.getTransactionHistory.mockReturnValue(
        throwError(() => ({ error: { errorCode: 'ACC-404', message: 'Account not found' } }))
      );

      store.loadTransactions(7);

      expect(store.error()).toEqual({ errorCode: 'ACC-404', message: 'Account not found' });
      expect(store.hasError()).toBe(true);
      expect(store.lastActionSuccess()).toBe(false);
      expect(store.loading()).toBe(false);
    });

    it('falls back to a generic error when the response has no body', () => {
      service.getTransactionHistory.mockReturnValue(throwError(() => ({ error: null })));

      store.loadTransactions(7);

      expect(store.error()).toEqual({ errorCode: 'UNKNOWN_ERROR', message: 'Failed to load transactions' });
    });
  });

  describe('deposit', () => {
    it('prepends the new transaction on success', () => {
      service.getTransactionHistory.mockReturnValue(of(history));
      store.loadTransactions(7);
      service.deposit.mockReturnValue(of(response({ transactionId: 'dep-1', amount: 250 })));

      store.deposit(7, 250);

      expect(service.deposit).toHaveBeenCalledWith(7, 250);
      expect(store.transactions()[0]).toEqual({
        transactionId: 'dep-1',
        transactionType: 'DEPOSIT',
        actionType: 'CREDIT',
        amount: 250,
        createdOn: '2026-10-05T10:00:00Z',
      });
      expect(store.transactionCount()).toBe(4);
      expect(store.lastAction()).toBe('deposit');
      expect(store.lastActionSuccess()).toBe(true);
    });

    it('records the server error on failure', () => {
      service.deposit.mockReturnValue(throwError(() => ({ error: { errorCode: 'TRANS-400', message: 'Invalid amount' } })));

      store.deposit(7, -1);

      expect(store.error()?.errorCode).toBe('TRANS-400');
      expect(store.lastActionSuccess()).toBe(false);
      expect(store.loading()).toBe(false);
    });

    it('uses a deposit-specific fallback error', () => {
      service.deposit.mockReturnValue(throwError(() => ({})));

      store.deposit(7, 10);

      expect(store.error()).toEqual({ errorCode: 'DEPOSIT_FAILED', message: 'Deposit failed' });
    });
  });

  describe('withdraw', () => {
    it('prepends the new transaction on success', () => {
      service.withdraw.mockReturnValue(
        of(response({ transactionId: 'wd-1', transactionType: 'WITHDRAW', actionType: 'DEBIT', amount: 40 }))
      );

      store.withdraw(7, 40);

      expect(service.withdraw).toHaveBeenCalledWith(7, 40);
      expect(store.transactions()[0].transactionId).toBe('wd-1');
      expect(store.totalWithdrawals()).toBe(40);
      expect(store.lastAction()).toBe('withdraw');
    });

    it('records the server error on failure', () => {
      service.withdraw.mockReturnValue(
        throwError(() => ({ error: { errorCode: 'TRANS-400', message: 'Insufficient funds' } }))
      );

      store.withdraw(7, 1_000_000);

      expect(store.error()?.message).toBe('Insufficient funds');
    });

    it('uses a withdraw-specific fallback error', () => {
      service.withdraw.mockReturnValue(throwError(() => ({})));

      store.withdraw(7, 10);

      expect(store.error()).toEqual({ errorCode: 'WITHDRAW_FAILED', message: 'Withdrawal failed' });
    });
  });

  it('clearError removes the current error', () => {
    service.withdraw.mockReturnValue(throwError(() => ({})));
    store.withdraw(7, 10);

    store.clearError();

    expect(store.error()).toBeNull();
    expect(store.hasError()).toBe(false);
  });
});
