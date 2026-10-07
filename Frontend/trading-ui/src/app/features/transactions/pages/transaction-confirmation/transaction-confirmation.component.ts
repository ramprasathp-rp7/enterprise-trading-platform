import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionsStore } from '../../transactions.store';
import { TransactionService } from '../../services/transaction.service';
import { TransactionHistoryEntry } from '../../models/transaction.models';
import { AccountsService } from '../../../../api/generated/trade';

/**
 * Transaction confirmation page
 *
 * Shown after a deposit or withdrawal completes: amount, direction and
 * transaction details, with a route to Transaction History.
 * Uses the just-created transaction from the store when available; on a reload
 * or a shared link it fetches the transaction by id instead.
 */
@Component({
  selector: 'app-transaction-confirmation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './transaction-confirmation.component.html',
  styleUrls: ['./transaction-confirmation.component.css'],
})
export class TransactionConfirmationComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly store = inject(TransactionsStore);
  private readonly transactionService = inject(TransactionService);
  private readonly authService = inject(CoreAuthService);
  private readonly accounts = inject(AccountsService);

  readonly transactionId = signal<string>('');
  readonly transaction = signal<TransactionHistoryEntry | null>(null);
  readonly loading = signal(false);
  readonly notFound = signal(false);
  /** The account's currency: deposits and withdrawals are always in it */
  readonly currency = signal<string | null>(null);

  readonly accountId = computed(() => this.authService.currentUser()?.accountId ?? null);
  readonly isCredit = computed(() => this.transaction()?.actionType === 'CREDIT');

  readonly title = computed(() => {
    switch (this.transaction()?.transactionType) {
      case 'DEPOSIT':
        return 'Deposit completed';
      case 'WITHDRAW':
        return 'Withdrawal completed';
      default:
        return 'Transaction completed';
    }
  });

  readonly summary = computed(() => {
    const tx = this.transaction();
    if (!tx) {
      return '';
    }
    const amount = this.formatAmount(tx.amount);
    return this.isCredit() ? `${amount} credited to your account.` : `${amount} debited from your account.`;
  });

  ngOnInit(): void {
    const transactionId = this.route.snapshot.paramMap.get('transactionId') ?? '';
    this.transactionId.set(transactionId);
    this.loadCurrency();

    const fromStore = this.store.transactions().find((t) => t.transactionId === transactionId);
    if (fromStore) {
      this.transaction.set(fromStore);
      return;
    }

    this.lookUpTransaction(transactionId);
  }

  private loadCurrency(): void {
    const accountId = this.accountId();
    if (!accountId) {
      return;
    }
    this.accounts.getBalance(accountId).subscribe({
      next: (balance) => this.currency.set(balance.currency ?? null),
      error: () => this.currency.set(null),
    });
  }

  formatAmount(value: number): string {
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? value
      : date.toLocaleString('en-US', {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        });
  }

  private lookUpTransaction(transactionId: string): void {
    const accountId = this.accountId();
    if (!transactionId || !accountId) {
      this.notFound.set(true);
      return;
    }

    this.loading.set(true);
    this.transactionService.getTransaction(accountId, transactionId).subscribe({
      next: (tx) => {
        this.transaction.set({
          transactionId: tx.transactionId,
          transactionType: tx.transactionType,
          actionType: tx.actionType,
          amount: tx.amount,
          createdOn: tx.createdOn,
        });
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }
}
