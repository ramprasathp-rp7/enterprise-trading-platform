-- Migration: Create analytics watermarks table for tracking incremental loads
-- Used by sprint-07-event-backbone analytics ETL to track progress across runs

CREATE TABLE IF NOT EXISTS analytics_watermarks (
    component               VARCHAR(64)     PRIMARY KEY,
    last_processed_created_on TIMESTAMPTZ,
    batch_id                UUID,
    last_run_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

-- Initialize for fact_trades component
INSERT INTO analytics_watermarks (component, last_processed_created_on, batch_id, last_run_at)
VALUES ('fact_trades', NULL, NULL, NULL)
ON CONFLICT (component) DO NOTHING;

COMMENT ON TABLE analytics_watermarks IS
    'Tracks watermarks for incremental analytics ETL loads. '
    'component: which part of the ETL (e.g., fact_trades). '
    'last_processed_created_on: the highest created_on timestamp from the last load. '
    'batch_id: UUID of the last batch run. '
    'last_run_at: when the last load completed.';
