import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../services/admin.service';
import { AccountsService } from '../../../../api/generated/trade';
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
  private accountsService = inject(AccountsService);

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
    this.successMessage.set('');
    this.accountDetails.set(null);
    this.positions.set([]);
    this.orders.set([]);

    this.loadAccountData(accountId);
  }

  private loadAccountData(accountId: number): void {
    Promise.all([
      firstValueFrom(this.accountsService.getAccount(accountId)),
      firstValueFrom(this.accountsService.getPositions(accountId)),
      firstValueFrom(this.accountsService.getOrders(accountId)),
    ])
      .then(([account, positions, orders]) => {
        console.log('Account data loaded:', { account, positions, orders });
        
        // Map account response to AccountDetails model
        if (account) {
          this.accountDetails.set({
            id: account.id,
            accountId: account.accountId,
            holderName: account.holderName,
            cashBalance: Number(account.cashBalance),
            status: account.status as 'ACTIVE' | 'SUSPENDED' | 'CLOSED',
            version: account.version,
            lastUpdated: new Date(account.lastUpdated),
          });
        }
        
        // Map position responses - PositionResponse has: accountId, symbol, quantity, averageCost
        if (positions) {
          this.positions.set(
            positions.map((p) => ({
              accountId: p.accountId,
              symbol: p.symbol,
              quantity: Number(p.quantity),
              averageCost: Number(p.averageCost),
            }))
          );
        }
        
        // Map order responses - OrderHistoryEntry has all required fields
        if (orders) {
          this.orders.set(
            orders.map((o) => ({
              orderId: o.orderId,
              accountId: o.accountId,
              symbol: o.symbol,
              side: o.side as 'BUY' | 'SELL',
              quantity: Number(o.quantity),
              price: Number(o.price),
              executedPrice: o.executedPrice ? Number(o.executedPrice) : undefined,
              status: o.status as 'PENDING' | 'FILLED' | 'PARTIALLY_FILLED' | 'REJECTED' | 'CANCELLED',
              idempotencyKey: o.idempotencyKey || '',
              createdOn: new Date(o.createdOn),
            }))
          );
        }
        
        this.loading.set(false);
        this.successMessage.set('Account data loaded successfully');
      })
      .catch((error) => {
        console.error('Failed to load account data:', error);
        this.loading.set(false);
        this.errorMessage.set('Failed to load account data. Please try again.');
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

  formatAmount(value: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  formatDate(value: Date | string | number | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }

    const raw = String(value).trim();
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
