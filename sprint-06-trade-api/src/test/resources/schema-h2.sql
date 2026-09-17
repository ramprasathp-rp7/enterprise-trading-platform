-- H2 Schema initialization for Sprint 6 Trade API tests

CREATE TABLE accounts (
    id            BIGSERIAL       PRIMARY KEY,
    account_id    VARCHAR(32)     NOT NULL,
    holder_name   VARCHAR(120)    NOT NULL,
    cash_balance  NUMERIC(18, 2)  NOT NULL DEFAULT 0,
    currency      CHAR(3)         NOT NULL DEFAULT 'USD',
    status        VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    version       INTEGER         NOT NULL DEFAULT 0,
    created_on    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_accounts_account_id UNIQUE (account_id),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_accounts_cash_balance_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT ck_accounts_version_non_negative CHECK (version >= 0)
);

CREATE TABLE instruments (
    id           BIGSERIAL     PRIMARY KEY,
    symbol       VARCHAR(20)   NOT NULL,
    name         VARCHAR(120)  NOT NULL,
    asset_class  VARCHAR(10)   NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'USD',
    tradable     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_on   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_instruments_symbol UNIQUE (symbol),
    CONSTRAINT ck_instruments_asset_class CHECK (asset_class IN ('EQUITY', 'FX', 'CRYPTO'))
);

CREATE TABLE orders (
    id               UUID            PRIMARY KEY,
    account_id       BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    instrument_id    BIGINT          NOT NULL REFERENCES instruments(id) ON DELETE RESTRICT,
    side             VARCHAR(4)      NOT NULL,
    quantity         INTEGER         NOT NULL,
    price            NUMERIC(18, 2)  NOT NULL,
    executed_price   NUMERIC(18, 2),
    status           VARCHAR(10)     NOT NULL DEFAULT 'NEW',
    idempotency_key  VARCHAR(100)    NOT NULL,
    created_on       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

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

CREATE INDEX ix_orders_account_created_on ON orders (account_id, created_on DESC);

CREATE INDEX ix_orders_account_open_created_on ON orders (account_id, created_on DESC)
    WHERE status = 'NEW';

CREATE INDEX ix_orders_created_on ON orders (created_on);

CREATE TABLE positions (
    account_id     BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    instrument_id  BIGINT          NOT NULL REFERENCES instruments(id) ON DELETE RESTRICT,
    quantity       INTEGER         NOT NULL DEFAULT 0,
    average_cost   NUMERIC(18, 6)  NOT NULL DEFAULT 0,
    version        INTEGER         NOT NULL DEFAULT 0,
    last_updated   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id, instrument_id),
    CONSTRAINT ck_positions_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_positions_average_cost_non_negative CHECK (average_cost >= 0)
);
