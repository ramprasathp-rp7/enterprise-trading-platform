-- Add two columns required by the Trade Executor for async order processing
-- executed_price already exists in migration 001; these two are new.

-- executed_on: The timestamp when the order was executed (filled, rejected, or cancelled)
-- This is separate from updated_on (which tracks any change to the row)
-- Set by the Trade Executor after settling the order
ALTER TABLE orders ADD COLUMN executed_on TIMESTAMPTZ;

-- rejected_reason: The reason the order was rejected or cancelled
-- Set by the Trade Executor only for REJECTED or CANCELLED orders
-- Examples: INSUFFICIENT_FUNDS, PRICE_NOT_MET, INSTRUMENT_NOT_TRADABLE, CANCELLED_BY_CUSTOMER, PRICE_UNAVAILABLE
ALTER TABLE orders ADD COLUMN rejected_reason VARCHAR(100);

-- Constraint: executed_on is only set on FILLED, REJECTED, or CANCELLED orders
ALTER TABLE orders ADD CONSTRAINT ck_orders_executed_on_iff_terminal CHECK (
    (status IN ('FILLED', 'REJECTED', 'CANCELLED') AND executed_on IS NOT NULL)
    OR (status = 'NEW')
);

-- Constraint: rejected_reason is only set on REJECTED or CANCELLED orders
ALTER TABLE orders ADD CONSTRAINT ck_orders_rejected_reason_iff_rejected CHECK (
    (status IN ('REJECTED', 'CANCELLED') AND rejected_reason IS NOT NULL)
    OR (status IN ('NEW', 'FILLED'))
);

-- Index for fetching executed orders by account (used by analytics ETL to load star schema)
CREATE INDEX ix_orders_account_executed_on ON orders (account_id, executed_on DESC)
    WHERE executed_on IS NOT NULL;

-- Index for fetching rejected/cancelled orders for audit or compliance
CREATE INDEX ix_orders_rejected_on ON orders (executed_on DESC)
    WHERE status IN ('REJECTED', 'CANCELLED');

COMMENT ON COLUMN orders.executed_on IS
    'The timestamp when the order was executed (filled, rejected, or cancelled). '
    'Set by the Trade Executor after settling the order. '
    'Different from updated_on: executed_on marks the settlement timestamp; updated_on tracks any row update. '
    'Null for NEW orders awaiting execution.';

COMMENT ON COLUMN orders.rejected_reason IS
    'The reason a REJECTED or CANCELLED order did not fill. Set by the Trade Executor. '
    'Examples: INSUFFICIENT_FUNDS, PRICE_NOT_MET, INSTRUMENT_NOT_TRADABLE, CANCELLED_BY_CUSTOMER, PRICE_UNAVAILABLE. '
    'Null for FILLED and NEW orders.';
