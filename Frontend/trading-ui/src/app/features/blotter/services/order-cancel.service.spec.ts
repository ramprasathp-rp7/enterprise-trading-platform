import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { OrderCancelService } from './order-cancel.service';
import { OrdersService } from '../../../api/generated/trade/api/orders.service';

describe('OrderCancelService', () => {
  let service: OrderCancelService;
  let ordersApi: { cancelOrder: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    ordersApi = { cancelOrder: vi.fn(() => of({ status: 'CANCELLED' })) };
    TestBed.configureTestingModule({
      providers: [{ provide: OrdersService, useValue: ordersApi }],
    });
    service = TestBed.inject(OrderCancelService);
  });

  it('calls DELETE /api/v1/orders/{id} with the bare UUID', () => {
    service.cancelOrder('6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e').subscribe();

    expect(ordersApi.cancelOrder).toHaveBeenCalledWith('6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e');
  });

  it('strips the ORD- display prefix', () => {
    service.cancelOrder('ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e').subscribe();

    expect(ordersApi.cancelOrder).toHaveBeenCalledWith('6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e');
  });
});
