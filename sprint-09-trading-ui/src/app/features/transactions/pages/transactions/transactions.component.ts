import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
  imports: [CommonModule, FormsModule],
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
      style: 'currency',
      currency: 'USD',
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
  formatDate(dateString: string): string {
    try {
      return new Date(dateString).toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return dateString;
    }
  }
}
