-- Migration 026: Instrument exchanges and account settings (Sprint 10)
--
-- 1. instruments.exchange
--    Which market an instrument trades on, as the Fauxnance symbol registry reports it
--    (GET /symbols/{symbol}: NYSE, NASDAQ, NSE, BSE, FX, CRYPTO). It drives the market preference
--    (USA = NYSE + NASDAQ, NSE, or all markets) and is filled for symbols added by symbol discovery.
--    Nullable: seed rows inserted after this migration have none, and the Trade REST API derives
--    it from the symbol when it is missing (InstrumentExchanges.derive), using the same rules as the
--    backfill below.
--
-- 2. account_settings
--    One row per account for the trading settings on the Preferences page:
--    - order_budget: the most an order should cost, in the account's currency. Placing an order
--      whose notional is above it shows a warning; it never blocks the order. NULL = no budget.
--    - market: which markets the instrument lists and searches show by default (USA, NSE, ALL).
--    - default_quantity: prefilled in the order ticket. NULL = no prefill.
--    No row means the defaults (no budget, ALL markets, no default quantity).
--    The colour theme is not here: it is a per-browser setting kept in the browser.

ALTER TABLE instruments ADD COLUMN exchange VARCHAR(10);

ALTER TABLE instruments ADD CONSTRAINT ck_instruments_exchange
    CHECK (exchange IS NULL OR exchange IN ('NYSE', 'NASDAQ', 'NSE', 'BSE', 'FX', 'CRYPTO'));

UPDATE instruments SET exchange = CASE
    WHEN symbol LIKE 'FX:%'  THEN 'FX'
    WHEN symbol LIKE 'X:%'   THEN 'CRYPTO'
    WHEN symbol LIKE '%.NS'  THEN 'NSE'
    WHEN symbol LIKE '%.BO'  THEN 'BSE'
    WHEN symbol IN ('PFE', 'JPM') THEN 'NYSE'
    ELSE 'NASDAQ'
END
WHERE exchange IS NULL;

COMMENT ON COLUMN instruments.exchange IS
    'Market the instrument trades on (Fauxnance registry). NULL for rows seeded after migration 026; '
    'the API derives it from the symbol.';

CREATE TABLE account_settings (
    account_id        BIGINT                   NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    order_budget      NUMERIC(18, 2),
    market            VARCHAR(8)               NOT NULL DEFAULT 'ALL',
    default_quantity  INTEGER,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_account_settings PRIMARY KEY (account_id),
    CONSTRAINT ck_as_order_budget CHECK (order_budget IS NULL OR order_budget > 0),
    CONSTRAINT ck_as_market CHECK (market IN ('USA', 'NSE', 'ALL')),
    CONSTRAINT ck_as_default_quantity CHECK (default_quantity IS NULL OR default_quantity > 0)
);

COMMENT ON TABLE account_settings IS
    'Trading settings per account: order budget (warning only), default market, default order quantity. '
    'No row means the defaults.';
