-- Watermark index: the incremental extract (DESIGN.md section 2) windows on
-- orders.updated_on, not created_on, because an order mutates in place
-- (NEW -> FILLED) and a created_on watermark would never re-read that
-- status change.
CREATE INDEX ix_orders_updated_on ON orders (updated_on);

-- Optimistic concurrency: Sprint 7 puts two writers (Trade Executor fills,
-- reconciliation jobs) on the same position row. version is bumped on every
-- UPDATE so a writer can detect it read a stale row (WHERE version = :expected)
-- instead of silently overwriting a concurrent change.
ALTER TABLE positions ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE positions ADD CONSTRAINT ck_positions_version_non_negative CHECK (version >= 0);
