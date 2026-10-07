-- Migration 014: Price alerts
--
-- A customer can ask to be told when an instrument's price crosses a level. Rows are created
-- through the Price Alert API (POST /api/v1/accounts/{accountId}/price-alerts); the price-alert
-- evaluator in the Trade REST API checks every new market-data quote against the active rows.
--
-- Crossed means: ABOVE fires when price >= threshold, BELOW fires when price <= threshold.
-- What happens next depends on mode:
--   ONCE        - fire on the first quote past the level, then deactivate (deactivated_reason = 'TRIGGERED_ONCE');
--   EVERY_QUOTE - fire on every new quote past the level; the alert stays active.
--
-- Every firing is recorded in price_alert_triggers with the reason and whether the hand-off to
-- notifications succeeded. The UI reads recent triggers to fill the notification bell, so there is
-- no separate notifications table.

CREATE TABLE price_alerts (
    alert_id              BIGSERIAL       PRIMARY KEY,
    account_id            BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol                VARCHAR(20)     NOT NULL REFERENCES instruments(symbol) ON DELETE CASCADE,
    direction             VARCHAR(5)      NOT NULL,
    threshold             NUMERIC(18, 6)  NOT NULL,
    mode                  VARCHAR(12)     NOT NULL,
    active                BOOLEAN         NOT NULL DEFAULT true,
    last_quote_at         TIMESTAMPTZ,
    last_triggered_at     TIMESTAMPTZ,
    last_triggered_price  NUMERIC(18, 6),
    trigger_count         INTEGER         NOT NULL DEFAULT 0,
    deactivated_reason    VARCHAR(40),
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_price_alerts_direction CHECK (direction IN ('ABOVE', 'BELOW')),
    CONSTRAINT ck_price_alerts_mode CHECK (mode IN ('ONCE', 'EVERY_QUOTE')),
    CONSTRAINT ck_price_alerts_threshold_positive CHECK (threshold > 0),
    CONSTRAINT ck_price_alerts_trigger_count CHECK (trigger_count >= 0)
);

-- Per-quote lookup: every quote for every symbol is checked, so "who cares about this symbol?"
-- must only touch active rows for that symbol.
CREATE INDEX ix_price_alerts_active_symbol ON price_alerts (symbol) WHERE active;

CREATE INDEX ix_price_alerts_account ON price_alerts (account_id, created_at);

-- The same live rule cannot be created twice for one account.
CREATE UNIQUE INDEX uq_price_alerts_active_rule
    ON price_alerts (account_id, symbol, direction, threshold, mode) WHERE active;

COMMENT ON TABLE price_alerts IS
    'Price levels a customer wants to be told about. Active rows are evaluated against every new '
    'market-data quote; the number of rows per account is capped by the API.';
COMMENT ON COLUMN price_alerts.last_quote_at IS
    'Reception time of the newest quote already evaluated for this alert, so a quote never fires twice.';
COMMENT ON COLUMN price_alerts.deactivated_reason IS
    'Why the alert stopped: TRIGGERED_ONCE (ONCE alert fired) or PAUSED (customer turned it off).';

CREATE TABLE price_alert_triggers (
    trigger_id       BIGSERIAL       PRIMARY KEY,
    alert_id         BIGINT          NOT NULL REFERENCES price_alerts(alert_id) ON DELETE CASCADE,
    account_id       BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    price            NUMERIC(18, 6)  NOT NULL,
    quote_at         TIMESTAMPTZ     NOT NULL,
    triggered_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    reason           TEXT            NOT NULL,
    delivery_status  VARCHAR(12)     NOT NULL,

    CONSTRAINT ck_price_alert_triggers_delivery CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'FAILED')),
    -- A quote fires a given alert at most once.
    CONSTRAINT uq_price_alert_triggers_alert_quote UNIQUE (alert_id, quote_at)
);

COMMENT ON COLUMN price_alert_triggers.delivery_status IS
    'PENDING while being handed to notifications, DELIVERED once the notifier accepted it, FAILED if '
    'that call raised (the alert stays active and the same quote is retried on the next evaluation, '
    'updating this row rather than adding a new one).';

-- Serves the notification bell's "triggers since" poll.
CREATE INDEX ix_price_alert_triggers_account_time ON price_alert_triggers (account_id, triggered_at DESC);

CREATE OR REPLACE FUNCTION set_price_alerts_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_price_alerts_set_updated
    BEFORE UPDATE ON price_alerts
    FOR EACH ROW
    EXECUTE FUNCTION set_price_alerts_updated_timestamp();
