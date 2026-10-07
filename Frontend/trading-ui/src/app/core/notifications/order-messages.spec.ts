import { describe, it, expect } from 'vitest';
import { orderPlacedNotice, orderStatusNotice } from './order-messages';

describe('order notification wording', () => {
  const order = { side: 'SELL', quantity: 1500, symbol: 'INFY.NS', price: 1520.5, executedPrice: null as number | null, status: 'NEW' };

  it('confirms a received order in plain language with readable numbers', () => {
    expect(orderPlacedNotice(order)).toEqual({
      kind: 'order-created',
      title: 'Order received',
      message: "We've received your order to sell 1,500 INFY.NS at ₹1,520.50. We'll let you know as soon as it's completed.",
    });
  });

  it('never reports success or failure at submission, even if the order completed straight away', () => {
    // Outcomes are announced only from the trade-events feed
    expect(orderPlacedNotice({ ...order, status: 'FILLED', executedPrice: 1521 }).kind).toBe('order-created');
    expect(orderPlacedNotice({ ...order, status: 'REJECTED' }).kind).toBe('order-created');
  });

  it('reports a fill at the executed price, falling back to the limit price', () => {
    expect(orderStatusNotice({ ...order, status: 'FILLED', executedPrice: 1521 })).toEqual({
      kind: 'order-filled',
      title: 'Order completed',
      message: 'Good news! You sold 1,500 INFY.NS at ₹1,521.00.',
    });
  });

  it('falls back to the limit price when no executed price is known', () => {
    expect(orderStatusNotice({ ...order, side: 'BUY', status: 'FILLED' })?.message).toBe('Good news! You bought 1,500 INFY.NS at ₹1,520.50.');
  });

  it('includes the rejection reason from the trade event', () => {
    expect(orderStatusNotice({ ...order, status: 'REJECTED', reason: 'Insufficient funds' })?.message).toBe(
      'Your order to sell 1,500 INFY.NS at ₹1,520.50 was rejected. Reason: Insufficient funds. Open your order blotter for details.',
    );
  });

  it('still reads well when the event has no order details', () => {
    const bare = { side: null, quantity: null, symbol: null, price: null, status: 'FILLED', executedPrice: 99.5 };
    expect(orderStatusNotice(bare)?.message).toBe('Good news! Your order was filled at 99.50.');
    expect(orderStatusNotice({ ...bare, status: 'REJECTED', executedPrice: null })?.message).toBe(
      'Your order was rejected. Open your order blotter for details.',
    );
    expect(orderStatusNotice({ ...bare, status: 'CANCELLED' })?.message).toBe('Your order has been cancelled.');
  });

  it('has nothing to announce for a NEW order', () => {
    expect(orderStatusNotice(order)).toBeNull();
  });
});
