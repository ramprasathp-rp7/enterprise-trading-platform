import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionsStore } from '../../transactions.store';
import { TransactionType } from '../../models/transaction.models';

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

  // State from store
  transactions = this.transactionsStore.transactions;
  loading = this.transactionsStore.loading;
  error = this.transactionsStore.error;

  // Filter state
  selectedTypeFilter = signal<TransactionType | 'ALL'>('ALL');
  transactionIdQuery = signal('');
  transactionTypes: (TransactionType | 'ALL')[] = ['ALL', 'DEPOSIT', 'WITHDRAW', 'FEE', 'BUY', 'SELL'];

  ngOnInit() {
    const user = this.authService.currentUser();
    const accountId = user?.accountId;

    if (!accountId) {
      console.error('No account ID found in auth context');
      return;
    }

    // Load initial transaction history
    this.transactionsStore.loadTransactions(accountId);
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

  clearTransactionIdQuery() {
    this.transactionIdQuery.set('');
  }

  /**
   * Format amount as currency
   */
  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
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
