-- 011_order_history_initial_state.sql
-- 
-- Ensures every order has a complete history, including its initial creation.
-- The first row of an order's history has no previous state (from_status = NULL).
-- This allows queries to see the complete lifecycle: creation → status changes.

ALTER TABLE order_history ALTER COLUMN from_status DROP NOT NULL;

CREATE OR REPLACE FUNCTION fn_order_history_initial()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO order_history (order_id, timestamp, from_status, to_status)
    VALUES (NEW.id, NEW.created_on, NULL, NEW.status);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_history_initial
AFTER INSERT ON orders
FOR EACH ROW
EXECUTE FUNCTION fn_order_history_initial();

COMMENT ON TRIGGER trg_order_history_initial ON orders IS
    'Records the order''s initial status (from_status NULL) so every order has a complete history.';

-- Backfill existing orders that have no initial row.
-- Initial status = the first recorded from_status, or the current status if the order never changed.
INSERT INTO order_history (order_id, timestamp, from_status, to_status)
SELECT o.id,
       o.created_on,
       NULL,
       COALESCE(
           (SELECT h.from_status
              FROM order_history h
             WHERE h.order_id = o.id
             ORDER BY h.timestamp ASC, h.id ASC
             LIMIT 1),
           o.status)
  FROM orders o
 WHERE NOT EXISTS (
        SELECT 1 FROM order_history h
         WHERE h.order_id = o.id AND h.from_status IS NULL);
