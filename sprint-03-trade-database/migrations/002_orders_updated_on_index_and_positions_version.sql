-- Watermark index
CREATE INDEX ix_orders_updated_on ON orders (updated_on);

-- Optimistic concurrency
ALTER TABLE positions ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE positions ADD CONSTRAINT ck_positions_version_non_negative CHECK (version >= 0);
