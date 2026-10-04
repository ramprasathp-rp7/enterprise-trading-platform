import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountsService } from '../../../api/generated/trade/api/accounts.service';
import { OrderHistoryEntry } from '../../../api/generated/trade';

/**
 * Order history service
 * 
 * Calls:
 * - GET /api/v1/accounts/{id}/orders
 */
@Injectable({
  providedIn: 'root',
})
export class OrderHistoryService {
  private accountsApi = inject(AccountsService);

  /**
   * Fetch order history for an account
   * Returns orders newest first
   */
  getOrderHistory(accountId: number): Observable<OrderHistoryEntry[]> {
    return this.accountsApi.getOrders(accountId);
  }
}
