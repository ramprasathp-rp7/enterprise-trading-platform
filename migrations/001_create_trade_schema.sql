-- gen_random_uuid() is in pgcrypto
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE accounts (
    id            BIGSERIAL       PRIMARY KEY,
    account_id    VARCHAR(32)     NOT NULL,
    holder_name   VARCHAR(120)    NOT NULL,
    cash_balance  NUMERIC(18, 2)  NOT NULL DEFAULT 0,
    currency      CHAR(3)         NOT NULL DEFAULT 'USD',
    status        VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    version       INTEGER         NOT NULL DEFAULT 0,
    created_on    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_updated  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_accounts_account_id UNIQUE (account_id),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_accounts_cash_balance_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT ck_accounts_version_non_negative CHECK (version >= 0)
);

COMMENT ON TABLE accounts IS
    'Trading accounts. Never deleted — a finished account is CLOSED, not removed, '
    'because historical orders and positions must keep referencing it.';
COMMENT ON COLUMN accounts.id IS
    'Internal surrogate key the rest of the platform passes around. This is '
    '"accountId" everywhere in trade-api.yaml except AccountResponse.accountId.';
COMMENT ON COLUMN accounts.account_id IS
    'Customer-facing reference, e.g. ACC-000001. Not interchangeable with accounts.id.';

CREATE TABLE instruments (
    id           BIGSERIAL     PRIMARY KEY,
    symbol       VARCHAR(20)   NOT NULL,
    name         VARCHAR(120)  NOT NULL,
    asset_class  VARCHAR(10)   NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'USD',
    tradable     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_on   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_instruments_symbol UNIQUE (symbol),
    CONSTRAINT ck_instruments_asset_class CHECK (asset_class IN ('EQUITY', 'FX', 'CRYPTO'))
);

COMMENT ON TABLE instruments IS
    'Reference data for tradable instruments. Never deleted — a delisted symbol '
    'is set tradable = false so old orders/holdings that reference it remain valid.';

CREATE TABLE orders (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    instrument_id    BIGINT          NOT NULL REFERENCES instruments(id) ON DELETE RESTRICT,
    side             VARCHAR(4)      NOT NULL,
    quantity         INTEGER         NOT NULL,
    price            NUMERIC(18, 2)  NOT NULL,
    executed_price   NUMERIC(18, 2),
    status           VARCHAR(10)     NOT NULL DEFAULT 'NEW',
    idempotency_key  VARCHAR(100)    NOT NULL,
    created_on       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_on       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_orders_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_orders_status CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_orders_price_positive CHECK (price > 0),
    CONSTRAINT ck_orders_executed_price_positive CHECK (executed_price IS NULL OR executed_price > 0),
    CONSTRAINT ck_orders_executed_price_iff_filled CHECK (
        (status = 'FILLED' AND executed_price IS NOT NULL)
        OR (status <> 'FILLED' AND executed_price IS NULL)
    )
);

COMMENT ON TABLE orders IS
    'Every order ever placed, including REJECTED and CANCELLED ones — the audit '
    'trail. "A customer who was refused four times last Tuesday is a compliance '
    'question, and the answer has to be in the database."';
COMMENT ON COLUMN orders.idempotency_key IS
    'Client-generated request identifier. UNIQUE constraint is the enforcement '
    'point — a read-then-write check races two concurrent identical requests.';

CREATE INDEX ix_orders_account_created_on ON orders (account_id, created_on DESC);

CREATE INDEX ix_orders_account_open_created_on ON orders (account_id, created_on DESC)
    WHERE status = 'NEW';

CREATE INDEX ix_orders_created_on ON orders (created_on);

CREATE TABLE positions (
    account_id     BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    instrument_id  BIGINT          NOT NULL REFERENCES instruments(id) ON DELETE RESTRICT,
    quantity       INTEGER         NOT NULL DEFAULT 0,
    average_cost   NUMERIC(18, 6)  NOT NULL DEFAULT 0,
    last_updated   TIMESTAMPTZ     NOT NULL DEFAULT now(),

    PRIMARY KEY (account_id, instrument_id),
    CONSTRAINT ck_positions_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_positions_average_cost_non_negative CHECK (average_cost >= 0)
);

COMMENT ON TABLE positions IS
    'Derived state: the net result of an account''s FILLED orders per '
    'instrument. Rebuildable by replaying orders — that reconciliation query '
    'is part of this sprint''s review evidence. GET /api/v1/accounts/{id}/positions '
    'omits quantity = 0 rows at the API layer; the row itself is kept so '
    'average_cost history is not lost.';

-- -----------------------------------------------------------------------------
-- Housekeeping trigger: keep last_updated / updated_on current on every UPDATE.
-- Timestamp bookkeeping only — does not touch accounts.version
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_TABLE_NAME = 'accounts' THEN
        NEW.last_updated := now();
    ELSE
        NEW.updated_on := now();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_set_updated
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_timestamp();

CREATE TRIGGER trg_orders_set_updated
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_timestamp();

CREATE OR REPLACE FUNCTION set_positions_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_positions_set_updated
    BEFORE UPDATE ON positions
    FOR EACH ROW
    EXECUTE FUNCTION set_positions_updated_timestamp();
