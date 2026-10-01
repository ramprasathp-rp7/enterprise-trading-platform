import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { OrderResponse, OrdersService, PlaceOrderRequest } from '../../../api/generated/trade';
import { OrderTicketSubmitInput } from '../order-ticket.models';

/**
 * Order ticket service
 * 
 * Calls:
 * - POST /api/v1/orders
 */
@Injectable({
  providedIn: 'root',
})
export class OrderTicketService {
  constructor(private ordersApi: OrdersService) {}

  placeOrder(accountId: number, input: OrderTicketSubmitInput): Observable<OrderResponse> {
    const request: PlaceOrderRequest = {
      accountId,
      symbol: input.symbol,
      side: input.side,
      quantity: input.quantity,
      price: input.price,
      idempotencyKey: crypto.randomUUID(),
    };

    return this.ordersApi.placeOrder(request);
  }
}
