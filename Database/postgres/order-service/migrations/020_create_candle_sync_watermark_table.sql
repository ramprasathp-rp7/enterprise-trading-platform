CREATE TABLE candle_sync_watermarks (
    id                  BIGSERIAL       PRIMARY KEY,
    instrument_id       BIGINT          NOT NULL UNIQUE REFERENCES instruments(id) ON DELETE CASCADE,
    last_synced_date    DATE            NOT NULL,
    sync_status         VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    last_sync_time      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    bootstrap_completed BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_sync_status CHECK (sync_status IN ('COMPLETED', 'IN_PROGRESS', 'FAILED'))
);

CREATE INDEX ix_watermarks_bootstrap ON candle_sync_watermarks (bootstrap_completed, sync_status);

COMMENT ON TABLE candle_sync_watermarks IS
    'Tracks synchronization progress for each instrument. last_synced_date indicates '
    'the most recent date for which candle data exists. Used to determine delta sync boundaries. '
    'bootstrap_completed flag ensures bootstrap runs only once per instrument.';
