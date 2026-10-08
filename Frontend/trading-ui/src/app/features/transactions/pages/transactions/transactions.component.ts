import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountsService } from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService, fileDate } from '../../../../shared/statement-pdf.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { TransactionsStore } from '../../transactions.store';
import { TransactionType } from '../../models/transaction.models';
import { formatMoney } from '../../../../shared/currency';

/**
 * Transactions Component
 * Displays transaction history
 */
@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './transactions.component.html',
  styleUrls: ['./transactions.component.css'],
})
export class TransactionsComponent implements OnInit {
  private readonly authService = inject(CoreAuthService);
  readonly transactionsStore = inject(TransactionsStore);
  private readonly statementPdf = inject(StatementPdfService);
  private readonly notifications = inject(NotificationService);
  private readonly accountsService = inject(AccountsService);

  // State from store
  transactions = this.transactionsStore.transactions;
  loading = this.transactionsStore.loading;
  error = this.transactionsStore.error;

  // Filter state
  selectedTypeFilter = signal<TransactionType | 'ALL'>('ALL');
  transactionIdQuery = signal('');
  transactionTypes: (TransactionType | 'ALL')[] = ['ALL', 'DEPOSIT', 'WITHDRAW', 'FEE', 'BUY', 'SELL'];
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);
  /** The account's currency: every transaction amount is in it */
  readonly currency = signal<string | null>(null);

  ngOnInit() {
    const user = this.authService.currentUser();
    const accountId = user?.accountId;

    if (!accountId) {
      console.error('No account ID found in auth context');
      return;
    }

    // Load initial transaction history
    this.transactionsStore.loadTransactions(accountId);
    this.accountsService.getBalance(accountId).subscribe({
      next: (balance) => this.currency.set(balance.currency ?? null),
      error: () => this.currency.set(null),
    });
  }

  /**
   * Filter transactions by type
   */
  getFilteredTransactions() {
    const filter = this.selectedTypeFilter();
    const idQuery = this.transactionIdQuery().trim().toLowerCase();
    const allTxns = this.transactions();

    return allTxns.filter((tx) => {
      const matchesType = filter === 'ALL' || tx.transactionType === filter;
      const matchesId = !idQuery || tx.transactionId.toLowerCase().includes(idQuery);
      return matchesType && matchesId;
    });
  }

  /** Download the transactions currently shown (filters applied) as a PDF statement. */
  async downloadStatement(): Promise<void> {
    const user = this.authService.currentUser();
    const transactions = this.getFilteredTransactions();
    const credits = transactions.filter((t) => t.actionType === 'CREDIT').reduce((sum, t) => sum + t.amount, 0);
    const debits = transactions.filter((t) => t.actionType === 'DEBIT').reduce((sum, t) => sum + t.amount, 0);

    const filters: string[] = [];
    if (this.selectedTypeFilter() !== 'ALL') {
      filters.push(`Type ${this.selectedTypeFilter()}`);
    }
    if (this.transactionIdQuery().trim()) {
      filters.push(`ID contains "${this.transactionIdQuery().trim()}"`);
    }

    this.downloading.set(true);
    this.downloadError.set(null);
    try {
      const accountNumber = await this.customerAccountNumber();
      const fileName = `transaction-statement-${accountNumber}-${fileDate()}`;
      await this.statementPdf.download({
        title: 'Transaction Statement',
        details: [
          ['Account', accountNumber],
          ['Prepared for', user?.username ?? 'N/A'],
          ['Filters', filters.length ? filters.join(', ') : 'None (all transactions)'],
        ],
        summary: [
          ['Transactions', String(transactions.length)],
          ['Total credits', this.formatCurrency(credits)],
          ['Total debits', this.formatCurrency(debits)],
          ['Net', this.formatCurrency(credits - debits)],
        ],
        columns: [
          { header: 'Date & time' },
          { header: 'Type' },
          { header: 'Direction' },
          { header: 'Amount', numeric: true },
          { header: 'Transaction ID' },
        ],
        rows: transactions.map((t) => [
          this.formatDate(t.createdOn),
          t.transactionType,
          t.actionType,
          this.formatCurrency(t.amount),
          t.transactionId,
        ]),
        emptyMessage: 'No transactions match the current filters.',
        fileName,
      });
      this.notifications.notify({
        kind: 'report',
        title: 'Report exported',
        message: `Transaction statement downloaded as ${fileName}.pdf.`,
        link: { path: '/transaction-history' },
      });
    } catch (err) {
      console.error('Failed to generate transaction statement', err);
      this.downloadError.set('Could not generate the statement. Please try again.');
    } finally {
      this.downloading.set(false);
    }
  }

  /** Customer-facing account number (e.g. ACC-000001) for statements, not the internal numeric key. */
  private async customerAccountNumber(): Promise<string> {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      throw new Error('No account for the current session');
    }
    const account = await firstValueFrom(this.accountsService.getAccount(accountId));
    if (!account?.accountId) {
      throw new Error('Account has no customer-facing number');
    }
    return account.accountId;
  }

  clearTransactionIdQuery() {
    this.transactionIdQuery.set('');
  }

  /**
   * Format amount in the account's currency, e.g. $1,234.50
   */
  formatCurrency(amount: number): string {
    return formatMoney(amount, this.currency());
  }

  /**
   * Get FontAwesome icon for transaction type
   */
  getTypeIcon(type: TransactionType): string {
    const icons: Record<TransactionType, string> = {
      DEPOSIT: 'arrow-down',
      WITHDRAW: 'arrow-up',
      FEE: 'percent',
      BUY: 'shopping-cart',
      SELL: 'tag',
    };
    return icons[type] || 'exchange';
  }

  /**
   * Get action type class (DEBIT/CREDIT)
   */
  getActionClass(action: string): string {
    return action === 'CREDIT' ? 'action-credit' : 'action-debit';
  }

  /**
   * Clear error message
   */
  dismissError() {
    this.transactionsStore.clearError();
  }

  /**
   * Format date for display
   */
  formatDate(dateString: string | number | null | undefined): string {
    if (dateString === null || dateString === undefined) {
      return '';
    }

    const raw = String(dateString).trim();
    if (!raw) {
      return '';
    }

    const numericPattern = /^-?\d+(\.\d+)?$/;
    if (numericPattern.test(raw)) {
      const numericValue = Number(raw);
      if (Number.isFinite(numericValue)) {
        const epochMs = Math.abs(numericValue) < 1e12 ? numericValue * 1000 : numericValue;
        const epochDate = new Date(epochMs);
        if (!Number.isNaN(epochDate.getTime())) {
          return epochDate.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
          });
        }
      }
    }

    const parsedDate = new Date(raw);
    if (!Number.isNaN(parsedDate.getTime())) {
      return parsedDate.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    }

    return raw;
  }
}
