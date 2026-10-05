CREATE TABLE account_history (
    account_id       BIGINT          NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    snapshot_date    TIMESTAMPTZ     NOT NULL,
    cash_balance     NUMERIC(18, 2)  NOT NULL,
    networth         NUMERIC(18, 2)  NOT NULL,
    created_on       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    PRIMARY KEY (account_id, snapshot_date),

    CONSTRAINT ck_account_history_cash_balance_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT ck_account_history_networth_non_negative CHECK (networth >= 0)
);


CREATE INDEX ix_account_history_account_created_on ON account_history (account_id, snapshot_date DESC);
CREATE INDEX ix_account_history_snapshot_date ON account_history (snapshot_date);