-- Migration 023: Automated trading strategies
--
-- A customer configures a rule once; the strategy evaluator in the Trade REST API checks it
-- against the market-data quote history and, when the rule's trigger fires, places an order
-- through POST /api/v1/orders on the customer's behalf (no confirmation).
--
-- Triggers fire on a change of state only, never while a state persists:
--   MA_CROSSOVER - short moving average crosses the long one (BELOW -> ABOVE = BUY, ABOVE -> BELOW = SELL)
--   BOLLINGER    - price leaves the bands (INSIDE -> BELOW_LOWER = BUY, INSIDE -> ABOVE_UPPER = SELL)
--
-- Every trigger, placed or not, is recorded in strategy_executions with the indicator values
-- and the reason, so the customer can see what the strategy did and why.

CREATE TABLE trading_strategies (
    strategy_id               BIGSERIAL       PRIMARY KEY,
    account_id                BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol                    VARCHAR(20)     NOT NULL REFERENCES instruments(symbol) ON DELETE CASCADE,
    strategy_type             VARCHAR(20)     NOT NULL,
    short_window              INTEGER,
    long_window               INTEGER,
    band_window               INTEGER,
    band_k                    NUMERIC(4, 2),
    trade_side                VARCHAR(10)     NOT NULL,
    quantity                  INTEGER         NOT NULL,
    max_position              INTEGER         NOT NULL,
    max_daily_notional        NUMERIC(18, 2)  NOT NULL,
    max_consecutive_failures  INTEGER         NOT NULL DEFAULT 3,
    active                    BOOLEAN         NOT NULL DEFAULT true,
    disabled_reason           VARCHAR(20),
    last_state                VARCHAR(12),
    last_evaluated_quote_at   TIMESTAMPTZ,
    consecutive_failures      INTEGER         NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_strategies_type CHECK (strategy_type IN ('MA_CROSSOVER', 'BOLLINGER')),
    CONSTRAINT ck_strategies_side CHECK (trade_side IN ('BUY_ONLY', 'SELL_ONLY', 'BOTH')),
    CONSTRAINT ck_strategies_ma_params CHECK (
        strategy_type <> 'MA_CROSSOVER'
        OR (short_window >= 2 AND long_window > short_window AND long_window <= 100)),
    CONSTRAINT ck_strategies_band_params CHECK (
        strategy_type <> 'BOLLINGER'
        OR (band_window >= 5 AND band_window <= 100 AND band_k > 0 AND band_k <= 5)),
    CONSTRAINT ck_strategies_quantity CHECK (quantity > 0),
    CONSTRAINT ck_strategies_max_position CHECK (max_position >= 0),
    CONSTRAINT ck_strategies_max_daily_notional CHECK (max_daily_notional > 0),
    CONSTRAINT ck_strategies_max_failures CHECK (max_consecutive_failures BETWEEN 1 AND 20),
    CONSTRAINT ck_strategies_failures CHECK (consecutive_failures >= 0),
    CONSTRAINT ck_strategies_disabled_reason CHECK (disabled_reason IS NULL OR disabled_reason IN ('USER', 'FAILURES')),
    CONSTRAINT ck_strategies_last_state CHECK (
        last_state IS NULL OR last_state IN ('ABOVE', 'BELOW', 'INSIDE', 'ABOVE_UPPER', 'BELOW_LOWER'))
);

-- The evaluator only touches active rows for one symbol at a time.
CREATE INDEX ix_trading_strategies_active_symbol ON trading_strategies (symbol) WHERE active;

CREATE INDEX ix_trading_strategies_account ON trading_strategies (account_id, created_at);

-- One live rule of each type per symbol per account.
CREATE UNIQUE INDEX uq_trading_strategies_active_rule
    ON trading_strategies (account_id, symbol, strategy_type) WHERE active;

COMMENT ON COLUMN trading_strategies.last_state IS
    'Indicator state after the last evaluation; a trigger is a change of this state.';
COMMENT ON COLUMN trading_strategies.last_evaluated_quote_at IS
    'Reception time of the newest quote already evaluated, so a quote is never evaluated twice.';
COMMENT ON COLUMN trading_strategies.disabled_reason IS
    'USER (customer turned it off) or FAILURES (stopped after max_consecutive_failures failed orders).';

CREATE TABLE strategy_executions (
    execution_id       BIGSERIAL       PRIMARY KEY,
    strategy_id        BIGINT          NOT NULL REFERENCES trading_strategies(strategy_id) ON DELETE CASCADE,
    account_id         BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol             VARCHAR(20)     NOT NULL,
    quote_received_at  TIMESTAMPTZ     NOT NULL,
    signal             VARCHAR(4)      NOT NULL,
    price              NUMERIC(18, 6)  NOT NULL,
    quantity           INTEGER         NOT NULL,
    notional           NUMERIC(18, 2)  NOT NULL,
    indicators         JSONB           NOT NULL DEFAULT '{}'::jsonb,
    reason             TEXT            NOT NULL,
    outcome            VARCHAR(24)     NOT NULL,
    order_id           VARCHAR(64),
    error_code         VARCHAR(20),
    error_message      TEXT,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_strategy_executions_signal CHECK (signal IN ('BUY', 'SELL')),
    CONSTRAINT ck_strategy_executions_outcome CHECK (
        outcome IN ('PLACED', 'FAILED', 'SKIPPED_DISABLED', 'SKIPPED_LIMIT', 'SKIPPED_NO_POSITION')),
    -- A quote triggers a given strategy at most once.
    CONSTRAINT uq_strategy_executions_strategy_quote UNIQUE (strategy_id, quote_received_at)
);

CREATE INDEX ix_strategy_executions_strategy_time ON strategy_executions (strategy_id, created_at DESC);

-- Serves the daily spend cap: placed notional per strategy since the start of the day.
CREATE INDEX ix_strategy_executions_placed ON strategy_executions (strategy_id, created_at) WHERE outcome = 'PLACED';

CREATE OR REPLACE FUNCTION set_trading_strategies_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trading_strategies_updated
    BEFORE UPDATE ON trading_strategies
    FOR EACH ROW EXECUTE FUNCTION set_trading_strategies_updated_timestamp();
