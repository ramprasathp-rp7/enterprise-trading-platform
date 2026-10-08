-- Migration 025: Track whether an order's latest event has reached Kafka, and when it was cancelled
--
-- Every order status has exactly one event:
--   NEW       -> ORDER_PLACED    on orders        (published and replayed by the Trade REST API)
--   CANCELLED -> ORDER_CANCELLED on trade-events  (published and replayed by the Trade REST API)
--   FILLED    -> ORDER_FILLED    on trade-events  (published and replayed by the Trade Executor)
--   REJECTED  -> ORDER_REJECTED  on trade-events  (published and replayed by the Trade Executor)
--
-- event_pending is set TRUE by the write that moves an order into a status whose event must be
-- published, and cleared once Kafka acknowledges that event. Clearing is conditional on the status
-- the event describes, so a late acknowledgement of an older event never clears a newer one. Each
-- service's replay job republishes its own pending events after a broker outage. SYNC-mode orders
-- publish nothing and never set the flag.
--
-- cancelled_on is when the customer cancelled. ORDER_CANCELLED carries it, live and replayed.
-- executed_on cannot hold it: ck_orders_executed_on_iff_terminal keeps it NULL on CANCELLED rows.

-- Superseded draft of this migration (orders.published_on), in case it was applied locally
DROP INDEX IF EXISTS ix_orders_unpublished;
ALTER TABLE orders DROP COLUMN IF EXISTS published_on;

-- Existing orders were published or are settled: they default to not pending
ALTER TABLE orders ADD COLUMN event_pending BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN orders.event_pending IS
    'TRUE while the event for the current status (ORDER_PLACED, ORDER_FILLED, ORDER_REJECTED or ORDER_CANCELLED) '
    'has not been acknowledged by Kafka. Replay jobs republish pending events.';

-- The replay jobs' lookup: pending orders by status, oldest first
CREATE INDEX ix_orders_event_pending ON orders (status, updated_on)
    WHERE event_pending;

ALTER TABLE orders ADD COLUMN cancelled_on TIMESTAMPTZ;

-- Best available time for orders cancelled before this column existed
UPDATE orders SET cancelled_on = updated_on WHERE status = 'CANCELLED';

COMMENT ON COLUMN orders.cancelled_on IS
    'When the customer cancelled the order. Carried by ORDER_CANCELLED, whether published live or replayed.';
