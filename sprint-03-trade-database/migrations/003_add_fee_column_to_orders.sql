-- Add fee column to orders table for tracking transaction fees
ALTER TABLE orders ADD COLUMN fee NUMERIC(18, 2) NOT NULL DEFAULT 0;

-- Constraint: fee must be non-negative
ALTER TABLE orders ADD CONSTRAINT ck_orders_fee_non_negative CHECK (fee >= 0);

-- Index for fee queries if needed (e.g., summarizing fees by account)
CREATE INDEX ix_orders_account_fee ON orders (account_id, fee DESC)
    WHERE status = 'FILLED';

COMMENT ON COLUMN orders.fee IS
    'Transaction fee debited at order fill time. Calculated as flat_fee + (notional * percentage_fee / 100) '
    'based on the instrument asset class. Only set when order is FILLED.';
