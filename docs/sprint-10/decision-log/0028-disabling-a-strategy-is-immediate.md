# 0013 Disabling a strategy takes effect immediately by locking its row before an order is sent

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Lavi Vasudevan |

## Context

The brief requires a disabled strategy to stop placing orders now, not at the end of its evaluation cycle,
and to be demonstrated live by disabling one mid-cycle. The evaluator reads strategies, decides, and then
calls the Trade API, so a disable can land between the read and the order.

## Options considered

| Option | For | Against |
|---|---|---|
| Check an in-memory flag | Fast | Not shared across instances; can lag |
| Re-read `active` just before sending | Simple | Still a race between the read and the send |
| `SELECT ... FOR UPDATE` on the strategy row for the whole decision and order | A disable either lands first and is seen, or waits for the order | The row is locked during the HTTP call |

## Decision

Lock the row (`lockById`) in one transaction covering the guards, the order call and the execution record.
Disabling updates the same row, so the two are serialised. If the disable wins, the trigger is recorded as
`SKIPPED_DISABLED` and nothing is sent. The guards run inside the same lock: a sell needs the quantity held,
a buy cannot exceed the max holding, daily placed value is capped, and `max_consecutive_failures` failed
orders turn the strategy off.

## Consequences

A disable can wait up to the order timeout (5 seconds) behind an order already in flight, and that order is
not undone. For the demo, `TRADING_STRATEGY_PAUSE_BEFORE_ORDER_MS` widens the gap. The transaction holds a
database connection during the HTTP call, which is acceptable at this scale.
