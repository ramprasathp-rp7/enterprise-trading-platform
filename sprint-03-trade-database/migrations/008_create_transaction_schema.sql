CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE transactions (
    transaction_id   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       BIGINT         NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    order_id         UUID           REFERENCES orders(id) ON DELETE SET NULL,
    transaction_type  VARCHAR(10)   NOT NULL,
    action_type       VARCHAR(6)    NOT NULL,
    amount          NUMERIC(18, 2)  NOT NULL,
    created_on      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_transactions_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAW', 'FEE', 'BUY', 'SELL')),
    CONSTRAINT ck_transactions_action CHECK (action_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_transactions_order_id_required_for_trade_types CHECK (
        (transaction_type IN ('BUY', 'SELL','FEE') AND order_id IS NOT NULL)
        OR (transaction_type IN ('DEPOSIT', 'WITHDRAW') AND order_id IS NULL)
    ),
    CONSTRAINT ck_transactions_action_matches_type CHECK (
        (transaction_type IN ('DEPOSIT', 'SELL') AND action_type = 'CREDIT')
        OR (transaction_type IN ('WITHDRAW', 'FEE', 'BUY') AND action_type = 'DEBIT')
    ),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0)
);

COMMENT ON TABLE transactions IS
    'Entries for cash movements and trade settlements.';

CREATE OR REPLACE FUNCTION validate_transactions_account_id()
RETURNS TRIGGER AS $$
DECLARE
    order_account_id BIGINT;
BEGIN
    IF NEW.order_id IS NOT NULL THEN
        SELECT account_id
          INTO order_account_id
          FROM orders
         WHERE id = NEW.order_id;

        IF NEW.account_id <> order_account_id THEN
            RAISE EXCEPTION 'transactions.account_id must match orders.account_id when order_id is set';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_validate_account
    BEFORE INSERT OR UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION validate_transactions_account_id();
