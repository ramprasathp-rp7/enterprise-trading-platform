-- Status history of the orders in 004_orders.sql.
--
-- The seed inserts orders straight in their final status, so the insert trigger from migration 011
-- (trg_order_history_initial) records a single row per order, e.g. NULL -> FILLED. Real orders are
-- placed as NEW and the executor moves them on, so those rows are replaced with the full trail:
--   every order        NULL -> NEW              at created_on
--   FILLED / REJECTED  NEW  -> FILLED/REJECTED  at executed_on
--   CANCELLED          NEW  -> CANCELLED        at updated_on (cancelled orders have no executed_on)
--   NEW                (stays NEW)

DELETE FROM order_history h
USING orders o
WHERE h.order_id = o.id AND o.idempotency_key LIKE 'SEED-%';

INSERT INTO order_history (order_id, timestamp, from_status, to_status)
SELECT o.id, o.created_on, NULL, 'NEW'
FROM orders o
WHERE o.idempotency_key LIKE 'SEED-%';

INSERT INTO order_history (order_id, timestamp, from_status, to_status)
SELECT o.id,
       CASE WHEN o.status = 'CANCELLED' THEN o.updated_on ELSE o.executed_on END,
       'NEW',
       o.status
FROM orders o
WHERE o.idempotency_key LIKE 'SEED-%' AND o.status <> 'NEW';
