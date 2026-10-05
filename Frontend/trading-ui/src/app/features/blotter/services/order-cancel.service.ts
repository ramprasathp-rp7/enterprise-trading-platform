import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OrdersService } from '../../../api/generated/trade/api/orders.service';
import { OrderResponse } from '../../../api/generated/trade';

/**
 * Order cancellation service
 *
 * Calls:
 * - DELETE /api/v1/orders/{id}
 */
@Injectable({
  providedIn: 'root',
})
export class OrderCancelService {
  private ordersApi = inject(OrdersService);

  /**
   * Cancel a working (NEW) order.
   * The endpoint expects the bare UUID, so any `ORD-` display prefix is stripped.
   */
  cancelOrder(orderId: string): Observable<OrderResponse> {
    return this.ordersApi.cancelOrder(OrderCancelService.toOrderUuid(orderId));
  }

  static toOrderUuid(orderId: string): string {
    return orderId.trim().replace(/^ORD-/i, '');
  }
}
