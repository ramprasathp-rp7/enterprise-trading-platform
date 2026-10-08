# 0013 Order outcomes come only from the `trade-events` topic, are stored once per event, and are polled by cursor

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | raahulkumardebug (notifications) |

## Context

A customer must hear when an order is filled, rejected or cancelled. The outcome is decided later, by
executor-service, after the order has been accepted: the place-order response only says the order was
received. The executor publishes every outcome to the `trade-events` Kafka topic.

The first version of the bell announced "order placed, executed and rejected" from the browser, working
the outcome out client-side. The browser only knows what the place-order response told it, so it cannot see
a rejection that happens afterwards, and it never learns about orders placed elsewhere, such as a
strategy's.

Trade events omit the symbol, side and quantity, and Kafka delivers at least once.

## Options considered

| Option | For | Against |
|---|---|---|
| The browser infers the outcome (from the place-order response or by polling order history) | No backend work | A guess is not an outcome. Misses orders placed by strategies or in another tab |
| The executor calls a Trade API endpoint over HTTP | Immediate | A new dependency from executor-service to the Trade API, and a failed call loses the notice unless it is retried |
| The Trade API consumes `trade-events`, stores one row per event, and the UI polls the rows | Kafka already carries the real outcome. Redelivery is handled by the database. Covers every source of orders | The bell is not instant |
| Push to the browser (WebSocket or server-sent events) | Instant | New infrastructure and connection handling for a screen that is only glanced at |

## Decision

`TradeEventNotificationConsumer` (consumer group `order-notifications`) turns each `ORDER_FILLED`,
`ORDER_REJECTED` and `ORDER_CANCELLED` event into a row in `order_notifications`, and the browser reads them
with `GET /api/v1/accounts/{id}/order-notifications?after=<cursor>`. The bell shows no order outcome from any
other source. "Order received" is still created in the browser at placement, because it is not an outcome.

The row's `event_id` is unique, so a redelivered event is stored once, and email and SMS are sent
only when the insert actually added a row (decision 0014). Failure handling:

- A malformed or irrelevant message is logged, skipped and acknowledged.
- A database failure is rethrown, so the offset stays uncommitted and the message is retried.
- Missing symbol, side or quantity are filled from the `orders` table.

## Consequences

- The UI polls: every 5 seconds for 10 minutes after the customer places an order here, every 30 seconds
  otherwise (`OrderStatusWatcherService`). A fill can show up to 5 to 30 seconds late.
- The browser keeps a cursor (the largest notification id seen) in localStorage. With no cursor it only
  records the newest id and shows nothing, so history is not replayed on first login. Outcomes older than an
  hour are skipped, and a cursor ahead of the feed (the table was recreated) is reset.
- A new browser or a cleared storage starts from "now" and does not show earlier outcomes.
- Events published before the consumer existed are never notified.
- Consumers of `trade-events` are independent of each other, since each has its own consumer group.
