import { Component, OnDestroy, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionsStore } from '../../transactions.store';

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
  lastAction = this.transactionsStore.lastAction;
  lastActionSuccess = this.transactionsStore.lastActionSuccess;
  showSuccess = signal(false);
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
    const action = this.lastAction();
    if (action === 'deposit') {
      return 'Amount credited successfully.';
    }
    if (action === 'withdraw') {
      return 'Amount debited successfully.';
    }
    return '';
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
