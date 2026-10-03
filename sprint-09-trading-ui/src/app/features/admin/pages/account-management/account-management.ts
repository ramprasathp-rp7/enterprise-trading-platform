import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AccountDetails, Position, Order } from '../../admin.models';

@Component({
  selector: 'app-admin-account-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account-management.html',
  styleUrls: ['./account-management.css'],
})
export class AdminAccountManagementComponent implements OnInit {
  private adminService = inject(AdminService);

  searchAccountId = signal<string>('');
  accountDetails = signal<AccountDetails | null>(null);
  positions = signal<Position[]>([]);
  orders = signal<Order[]>([]);
  
  loading = signal<boolean>(false);
  errorMessage = signal<string>('');
  successMessage = signal<string>('');
  activeTab = signal<'positions' | 'orders'>('positions');

  ngOnInit(): void {}

  lookupAccount(): void {
    const accountId = parseInt(this.searchAccountId(), 10);
    if (!accountId || accountId <= 0) {
      this.errorMessage.set('Please enter a valid account ID');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.accountDetails.set(null);
    this.positions.set([]);
    this.orders.set([]);

    this.adminService.getAccountDetails(accountId).subscribe({
      next: (data) => {
        this.accountDetails.set(data);
        this.loadAccountData(accountId);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Account not found');
        console.error(err);
        this.loading.set(false);
      },
    });
  }

  private loadAccountData(accountId: number): void {
    this.adminService.getAccountPositions(accountId).subscribe({
      next: (data) => this.positions.set(data),
      error: (err) => console.error('Failed to load positions:', err),
    });

    this.adminService.getAccountOrders(accountId).subscribe({
      next: (data) => this.orders.set(data),
      error: (err) => console.error('Failed to load orders:', err),
    });
  }

  setActiveTab(tab: 'positions' | 'orders'): void {
    this.activeTab.set(tab);
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
