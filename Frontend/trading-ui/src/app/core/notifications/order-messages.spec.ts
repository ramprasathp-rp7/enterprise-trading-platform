import { describe, it, expect } from 'vitest';
import { orderPlacedNotice, orderStatusNotice } from './order-messages';

describe('order notification wording', () => {
  const order = { side: 'SELL', quantity: 1500, symbol: 'INFY.NS', price: 1520.5, executedPrice: null as number | null, status: 'NEW' };

  it('confirms a received order in plain language with readable numbers', () => {
    expect(orderPlacedNotice(order)).toEqual({
      kind: 'order-created',
      title: 'Order received',
      message: "We've received your order to sell 1,500 INFY.NS at 1,520.50. We'll let you know as soon as it's completed.",
    });
  });

  it('reports an order that completed straight away as completed', () => {
    expect(orderPlacedNotice({ ...order, status: 'FILLED', executedPrice: 1521 })).toEqual({
      kind: 'order-filled',
      title: 'Order completed',
      message: 'Good news! You sold 1,500 INFY.NS at 1,521.00.',
    });
  });

  it('falls back to the limit price when no executed price is known', () => {
    expect(orderStatusNotice({ ...order, side: 'BUY', status: 'FILLED' })?.message).toBe('Good news! You bought 1,500 INFY.NS at 1,520.50.');
  });

  it('has nothing to announce for a NEW order', () => {
    expect(orderStatusNotice(order)).toBeNull();
  });
});
