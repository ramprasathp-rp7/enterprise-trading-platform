import { NotificationKind } from './notification.service';
import { formatInstrumentPrice } from '../../features/instruments/services/instrument-catalog.service';

/** The order fields the notification wording needs. */
export interface OrderLike {
  side: string;
  quantity: number;
  symbol: string;
  price: number;
  executedPrice?: number | null;
  status: string;
}

export interface OrderNotice {
  kind: NotificationKind;
  title: string;
  message: string;
}

const units = (n: number) => n.toLocaleString('en-US');
/** A price in the instrument's currency, e.g. $189.35 or ₹1,600.00 */
const money = (n: number, symbol: string) => formatInstrumentPrice(n, symbol);
const isSell = (o: OrderLike) => o.side?.toUpperCase() === 'SELL';

/** "buy 10 AAPL at 189.35" */
const describe = (o: OrderLike) => `${isSell(o) ? 'sell' : 'buy'} ${units(o.quantity)} ${o.symbol} at ${money(o.price, o.symbol)}`;

/**
 * Plain-language notice for an order status change, or null for statuses
 * that aren't worth announcing.
 */
export function orderStatusNotice(o: OrderLike): OrderNotice | null {
  switch (o.status) {
    case 'FILLED':
      return {
        kind: 'order-filled',
        title: 'Order completed',
        message: `Good news! You ${isSell(o) ? 'sold' : 'bought'} ${units(o.quantity)} ${o.symbol} at ${money(o.executedPrice ?? o.price, o.symbol)}.`,
      };
    case 'REJECTED':
      return {
        kind: 'order-rejected',
        title: "Order couldn't be completed",
        message: `Your order to ${describe(o)} was rejected. Open your order blotter for details.`,
      };
    case 'CANCELLED':
      return {
        kind: 'order-cancelled',
        title: 'Order cancelled',
        message: `Your order to ${describe(o)} has been cancelled.`,
      };
    default:
      return null;
  }
}

/** Notice shown right after an order is submitted from the ticket. */
export function orderPlacedNotice(o: OrderLike): OrderNotice {
  return (
    orderStatusNotice(o) ?? {
      kind: 'order-created',
      title: 'Order received',
      message: `We've received your order to ${describe(o)}. We'll let you know as soon as it's completed.`,
    }
  );
}
