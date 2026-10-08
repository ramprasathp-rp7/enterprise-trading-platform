import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { AccountsService } from '../../../../api/generated/trade';
import { TransactionsStore } from '../../transactions.store';
import { TransactionService } from '../../services/transaction.service';
import { TransactionHistoryEntry } from '../../models/transaction.models';

/** One-tap amounts offered under the amount field */
const QUICK_AMOUNTS = [100, 500, 1_000, 5_000];
/** How many recent transactions to show in the side rail */
const RECENT_LIMIT = 4;

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
  private readonly accounts = inject(AccountsService);
  private readonly transactionService = inject(TransactionService);

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

  readonly quickAmounts = QUICK_AMOUNTS;
  /** Current cash balance (null until loaded or if unavailable) */
  readonly balance = signal<number | null>(null);
  /** The account's currency, from its balance; blank until that has loaded */
  readonly currency = signal('');
  /** Customer-facing account number, e.g. ACC-000007 */
  readonly accountNumber = signal<string | null>(null);
  readonly recentTransactions = signal<TransactionHistoryEntry[]>([]);

  /** Balance once this deposit / withdrawal goes through */
  readonly balanceAfter = computed(() => {
    const balance = this.balance();
    const amount = this.amount();
    if (balance === null) {
      return null;
    }
    const delta = amount && amount > 0 ? amount : 0;
    return this.transactionType() === 'deposit' ? balance + delta : balance - delta;
  });

  /** A withdrawal larger than the available cash (the server has the final say) */
  readonly exceedsBalance = computed(() => {
    const balance = this.balance();
    const amount = this.amount();
    return this.transactionType() === 'withdraw' && balance !== null && !!amount && amount > balance;
  });

  constructor() {
    this.loadAccountOverview();

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
    // The API accepts at most two decimal places (VAL-422 otherwise), so a typed 10.555 is sent as
    // 10.56, the same rounding the database applied before the API started enforcing it.
    const amount = roundToCents(this.amount());
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

  /** Add a quick amount to whatever is already entered */
  addQuickAmount(value: number) {
    const current = this.amount();
    this.amount.set(Math.round(((current && current > 0 ? current : 0) + value) * 100) / 100);
  }

  /** Withdraw everything that is available */
  useFullBalance() {
    const balance = this.balance();
    if (balance !== null && balance > 0) {
      this.amount.set(balance);
    }
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
    return this.transactionType() === 'deposit' ? 'Deposit funds' : 'Withdraw funds';
  }

  getTransactionDescription(): string {
    return this.transactionType() === 'deposit'
      ? 'Add funds securely to your account.'
      : 'Move funds out of your account with full audit visibility.';
  }

  getActionLabel(): string {
    return this.transactionType() === 'deposit' ? 'Deposit' : 'Withdraw';
  }

  transactionIcon(type: string): string {
    switch (type) {
      case 'DEPOSIT':
        return 'fa-arrow-down';
      case 'WITHDRAW':
        return 'fa-arrow-up';
      case 'FEE':
        return 'fa-receipt';
      default:
        return 'fa-right-left';
    }
  }

  /** "Oct 5, 9:00 AM" for ISO strings or epoch seconds/milliseconds; '' when unreadable */
  shortDate(value: string | number | null | undefined): string {
    if (value === null || value === undefined || String(value).trim() === '') {
      return '';
    }
    const raw = String(value).trim();
    const numeric = /^-?\d+(\.\d+)?$/.test(raw) ? Number(raw) : null;
    const date = numeric !== null ? new Date(Math.abs(numeric) < 1e12 ? numeric * 1000 : numeric) : new Date(raw);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
  }

  /** Balance, account number and recent activity for the side rail */
  private loadAccountOverview(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      return;
    }
    this.accounts.getBalance(accountId).subscribe({
      next: (b) => {
        this.balance.set(b.cashBalance);
        if (b.currency) {
          this.currency.set(b.currency);
        }
      },
      error: () => this.balance.set(null),
    });
    this.accounts.getAccount(accountId).subscribe({
      next: (a) => this.accountNumber.set(a.accountId),
      error: () => this.accountNumber.set(null),
    });
    this.transactionService.getTransactionHistory(accountId).subscribe({
      next: (rows) => this.recentTransactions.set((rows ?? []).slice(0, RECENT_LIMIT)),
      error: () => this.recentTransactions.set([]),
    });
  }
}

/** Round half up to two decimal places, using decimal exponents so 1.005 becomes 1.01 rather than 1.00 */
function roundToCents(value: number | null | undefined): number | null {
  if (value == null || !Number.isFinite(value)) {
    return null;
  }
  return Number(`${Math.round(Number(`${value}e2`))}e-2`);
}
