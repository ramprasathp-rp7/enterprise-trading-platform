CREATE TABLE candle_data (
    id              BIGSERIAL       PRIMARY KEY,
    instrument_id   BIGINT          NOT NULL REFERENCES instruments(id) ON DELETE RESTRICT,
    date            DATE            NOT NULL,
    open            NUMERIC(18, 6)  NOT NULL,
    high            NUMERIC(18, 6)  NOT NULL,
    low             NUMERIC(18, 6)  NOT NULL,
    close           NUMERIC(18, 6)  NOT NULL,
    adjclose        NUMERIC(18, 6)  NOT NULL,
    volume          BIGINT          NOT NULL DEFAULT 0,
    synthetic       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_candle_data_date UNIQUE (instrument_id, date),
    CONSTRAINT ck_candle_data_prices CHECK (
        high >= low
        AND high >= open
        AND high >= close
        AND low <= open
        AND low <= close
    ),
    CONSTRAINT ck_candle_data_volume_non_negative CHECK (volume >= 0)
);

CREATE INDEX ix_candle_data_instrument_date ON candle_data (instrument_id, date DESC);
CREATE INDEX ix_candle_data_instrument_date_range ON candle_data (instrument_id, date) WHERE synthetic = FALSE;

COMMENT ON TABLE candle_data IS
    'Daily OHLCV candle data fetched from Fauxnance API. adjclose is adjusted '
    'for splits/dividends and should be used for returns calculations. synthetic = true '
    'indicates rows generated to bridge data gaps.';

CREATE OR REPLACE FUNCTION set_candle_data_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_candle_data_set_updated
    BEFORE UPDATE ON candle_data
    FOR EACH ROW
    EXECUTE FUNCTION set_candle_data_updated_timestamp();
