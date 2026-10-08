import { NotificationKind } from './notification.service';
import { formatInstrumentPrice } from '../../features/instruments/services/instrument-catalog.service';

/** The order fields the notification wording needs (details may be missing on a trade event). */
export interface OrderLike {
  side: string | null;
  quantity: number | null;
  symbol: string | null;
  price: number | null;
  executedPrice?: number | null;
  status: string;
  /** Why a rejected order was rejected, as reported on trade-events */
  reason?: string | null;
}

export interface OrderNotice {
  kind: NotificationKind;
  title: string;
  message: string;
}

const units = (n: number) => n.toLocaleString('en-US');
/** A price in the instrument's currency, e.g. $189.35 or ₹1,600.00 */
const money = (n: number, symbol: string | null) => formatInstrumentPrice(n, symbol);
const isSell = (o: OrderLike) => o.side?.toUpperCase() === 'SELL';
const hasDetails = (o: OrderLike) => !!o.symbol && o.quantity != null && o.price != null;

/** "buy 10 AAPL at $189.35", or '' when the details are unknown */
const describe = (o: OrderLike) =>
  hasDetails(o) ? `${isSell(o) ? 'sell' : 'buy'} ${units(o.quantity!)} ${o.symbol} at ${money(o.price!, o.symbol)}` : '';

/** " Reason: Insufficient funds." when the event carried one */
const reasonOf = (o: OrderLike) => (o.reason?.trim() ? ` Reason: ${o.reason.trim().replace(/\.$/, '')}.` : '');

/**
 * Plain-language notice for an order outcome from the trade-events feed, or null for statuses
 * that aren't worth announcing.
 */
export function orderStatusNotice(o: OrderLike): OrderNotice | null {
  const fillPrice = o.executedPrice ?? o.price;
  switch (o.status) {
    case 'FILLED':
      return {
        kind: 'order-filled',
        title: 'Order completed',
        message: o.symbol && o.quantity != null
          ? `Good news! You ${isSell(o) ? 'sold' : 'bought'} ${units(o.quantity)} ${o.symbol}${fillPrice != null ? ` at ${money(fillPrice, o.symbol)}` : ''}.`
          : `Good news! Your order was filled${fillPrice != null ? ` at ${money(fillPrice, o.symbol)}` : ''}.`,
      };
    case 'REJECTED':
      return {
        kind: 'order-rejected',
        title: "Order couldn't be completed",
        message: `${hasDetails(o) ? `Your order to ${describe(o)}` : 'Your order'} was rejected.${reasonOf(o)} Open your order blotter for details.`,
      };
    case 'CANCELLED':
      return {
        kind: 'order-cancelled',
        title: 'Order cancelled',
        message: `${hasDetails(o) ? `Your order to ${describe(o)}` : 'Your order'} has been cancelled.`,
      };
    default:
      return null;
  }
}

/**
 * Notice shown right after an order is submitted from the ticket. Always "received": whether the
 * order then fills or is rejected is only ever announced from the trade-events feed.
 */
export function orderPlacedNotice(o: OrderLike): OrderNotice {
  return {
    kind: 'order-created',
    title: 'Order received',
    message: hasDetails(o)
      ? `We've received your order to ${describe(o)}. We'll let you know as soon as it's completed.`
      : "We've received your order. We'll let you know as soon as it's completed.",
  };
}
