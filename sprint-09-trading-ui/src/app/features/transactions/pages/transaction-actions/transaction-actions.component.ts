import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionsStore } from '../../transactions.store';
import { TransactionHistoryEntry } from '../../models/transaction.models';

@Component({
  selector: 'app-transaction-actions',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transaction-actions.component.html',
  styleUrls: ['./transaction-actions.component.css'],
})
export class TransactionActionsComponent implements OnDestroy {
  private readonly authService = inject(CoreAuthService);
  readonly transactionsStore = inject(TransactionsStore);

  loading = this.transactionsStore.loading;
  error = this.transactionsStore.error;
  transactions = this.transactionsStore.transactions;
  lastAction = this.transactionsStore.lastAction;
  lastActionSuccess = this.transactionsStore.lastActionSuccess;
  accountId = computed(() => this.authService.currentUser()?.accountId ?? null);
  showSuccess = signal(false);
  lastSubmittedAmount = signal<number | null>(null);
  private successTimer: ReturnType<typeof setTimeout> | null = null;

  amount = signal<number | null>(null);
  transactionType = signal<'deposit' | 'withdraw'>('deposit');

  constructor() {
    effect(() => {
      const loading = this.loading();
      const action = this.lastAction();
      const success = this.lastActionSuccess();

      if (!loading && success && (action === 'deposit' || action === 'withdraw')) {
        this.showSuccess.set(true);

        if (this.successTimer) {
          clearTimeout(this.successTimer);
        }

        this.successTimer = setTimeout(() => {
          this.showSuccess.set(false);
          this.successTimer = null;
        }, 3500);
      }
    });
  }

  onSubmit() {
    const amount = this.amount();
    const accountId = this.authService.currentUser()?.accountId;

    if (!accountId || !amount || amount <= 0) {
      return;
    }

    if (this.transactionType() === 'deposit') {
      this.transactionsStore.deposit(accountId, amount);
    } else {
      this.transactionsStore.withdraw(accountId, amount);
    }

    this.lastSubmittedAmount.set(amount);

    this.amount.set(null);
  }

  onTransactionTypeChange(value: string) {
    if (value === 'deposit' || value === 'withdraw') {
      this.transactionType.set(value);
    }
  }

  dismissError() {
    this.transactionsStore.clearError();
  }

  ngOnDestroy() {
    if (this.successTimer) {
      clearTimeout(this.successTimer);
      this.successTimer = null;
    }
  }

  getSuccessMessage(): string {
    const amount = this.getLatestSuccessfulTransaction()?.amount;
    const formattedAmount = amount !== undefined ? this.formatAmount(amount) : null;
    const action = this.lastAction();
    if (action === 'deposit') {
      return formattedAmount ? `${formattedAmount} credited successfully.` : 'Amount credited successfully.';
    }
    if (action === 'withdraw') {
      return formattedAmount ? `${formattedAmount} debited successfully.` : 'Amount debited successfully.';
    }
    return '';
  }

  getLatestSuccessfulTransaction(): TransactionHistoryEntry | null {
    const latest = this.transactions()[0] ?? null;
    if (!latest) {
      return null;
    }

    const action = this.lastAction();
    const expectedType = action === 'deposit' ? 'DEPOSIT' : action === 'withdraw' ? 'WITHDRAW' : null;
    if (!expectedType) {
      return null;
    }

    return latest.transactionType === expectedType ? latest : null;
  }

  getLastAttemptedActionLabel(): string {
    return this.lastAction() === 'withdraw' ? 'Withdrawal' : 'Deposit';
  }

  getLastAttemptedAmountLabel(): string {
    const amount = this.lastSubmittedAmount();
    return amount && amount > 0 ? this.formatAmount(amount) : 'N/A';
  }

  getLatestSuccessfulAmountLabel(): string {
    const amount = this.getLatestSuccessfulTransaction()?.amount;
    return amount !== undefined ? this.formatAmount(amount) : 'N/A';
  }

  getCreatedOnLabel(value: string | null | undefined): string {
    if (!value) {
      return 'N/A';
    }

    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
  }

  private formatAmount(value: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  getTransactionTitle(): string {
    return this.transactionType() === 'deposit' ? 'Credit Account' : 'Debit Account';
  }

  getTransactionDescription(): string {
    return this.transactionType() === 'deposit'
      ? 'Add funds securely to your account.'
      : 'Move funds out with full audit visibility.';
  }

  getActionLabel(): string {
    return this.transactionType() === 'deposit' ? 'Credit' : 'Debit';
  }
}
