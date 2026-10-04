-- Order status history table - tracks all status changes for each order.
-- Automatically populated by trigger when order status is updated.

CREATE TABLE order_history (
    id               BIGSERIAL       PRIMARY KEY,
    order_id         UUID            NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    timestamp        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    from_status      VARCHAR(10)     NOT NULL,
    to_status        VARCHAR(10)     NOT NULL,

    CONSTRAINT ck_order_history_from_status CHECK (
        from_status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_order_history_to_status CHECK (
        to_status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')
    )
);

COMMENT ON TABLE order_history IS
    'Immutable audit trail of every order status transition. Never deleted. '
    'Records when an order changes state (NEW → FILLED, REJECTED, CANCELLED). '
    'Automatically populated by trigger when order status updates.';

-- Indexes for common queries
-- Query 1: Get complete status history for an order, sorted newest-first
-- Query 2: Time-range queries for audit/compliance reports (all rejections in last 24h, etc.)

CREATE INDEX ix_order_history_order_id_timestamp 
    ON order_history (order_id, timestamp DESC);

CREATE INDEX ix_order_history_timestamp 
    ON order_history (timestamp DESC);

-- Trigger to automatically log order status changes to order_history table.
-- Fires AFTER UPDATE of status column on orders table.

CREATE OR REPLACE FUNCTION fn_order_history_tracking()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status <> OLD.status THEN
        INSERT INTO order_history (order_id, from_status, to_status)
        VALUES (OLD.id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_history_tracking
AFTER UPDATE OF status ON orders
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION fn_order_history_tracking();

COMMENT ON TRIGGER trg_order_history_tracking ON orders IS
    'Automatically records every order status change to order_history for audit purposes.';