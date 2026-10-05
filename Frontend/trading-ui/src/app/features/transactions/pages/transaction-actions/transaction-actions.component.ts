import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionsStore } from '../../transactions.store';

@Component({
  selector: 'app-transaction-actions',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './transaction-actions.component.html',
  styleUrls: ['./transaction-actions.component.css'],
})
export class TransactionActionsComponent {
  private readonly authService = inject(CoreAuthService);
  private readonly router = inject(Router);
  readonly transactionsStore = inject(TransactionsStore);

  loading = this.transactionsStore.loading;
  error = this.transactionsStore.error;
  transactions = this.transactionsStore.transactions;
  lastAction = this.transactionsStore.lastAction;
  lastActionSuccess = this.transactionsStore.lastActionSuccess;
  accountId = computed(() => this.authService.currentUser()?.accountId ?? null);
  lastSubmittedAmount = signal<number | null>(null);
  /** True between this page's submit and the store reporting its outcome */
  private readonly awaitingResult = signal(false);

  amount = signal<number | null>(null);
  transactionType = signal<'deposit' | 'withdraw'>('deposit');

  constructor() {
    effect(() => {
      const loading = this.loading();
      const action = this.lastAction();
      const success = this.lastActionSuccess();

      // The store is app-wide, so only react to a submission made from this page.
      if (loading || !this.awaitingResult()) {
        return;
      }
      this.awaitingResult.set(false);

      if (success && (action === 'deposit' || action === 'withdraw')) {
        const created = untracked(this.transactions)[0];
        if (created?.transactionId) {
          this.router.navigate(['/transaction-confirmation', created.transactionId]);
        }
      }
    });
  }

  onSubmit() {
    const amount = this.amount();
    const accountId = this.authService.currentUser()?.accountId;

    if (!accountId || !amount || amount <= 0) {
      return;
    }

    this.awaitingResult.set(true);
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

  getLastAttemptedActionLabel(): string {
    return this.lastAction() === 'withdraw' ? 'Withdrawal' : 'Deposit';
  }

  getLastAttemptedAmountLabel(): string {
    const amount = this.lastSubmittedAmount();
    return amount && amount > 0 ? this.formatAmount(amount) : 'N/A';
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
