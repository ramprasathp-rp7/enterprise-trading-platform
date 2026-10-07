-- Migration 022: Multi-currency support
--
-- Orders are placed and executed in the instrument's currency; account money (cash, transactions,
-- fees, P&L, net worth) is in the account's currency. Conversion uses Fauxnance FX: pair quotes.
--
-- Two rates are kept per order because an order can sit in NEW (e.g. while the executor is stopped):
--   placement_fx_rate - captured by the Trade REST API at placement; used for the funds check and
--                       the reservation of a NEW order.
--   fill_fx_rate      - fetched fresh by the executor at fill; used for settlement, the fee and
--                       realised P&L. This is the rate of record for a FILLED order.
-- Both are "account-currency units per one instrument-currency unit" (1 when the currencies match).
-- See docs/sprint-10/decision-log/0008-multi-currency-placement-and-fill-rates.md.

ALTER TABLE orders ADD COLUMN placement_fx_rate NUMERIC(20, 10) NOT NULL DEFAULT 1;
ALTER TABLE orders ADD COLUMN fill_fx_rate      NUMERIC(20, 10);
ALTER TABLE orders ADD COLUMN realised_pnl      NUMERIC(18, 2);

ALTER TABLE orders ADD CONSTRAINT ck_orders_placement_fx_rate_positive CHECK (placement_fx_rate > 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_fill_fx_rate_positive CHECK (fill_fx_rate IS NULL OR fill_fx_rate > 0);

COMMENT ON COLUMN orders.placement_fx_rate IS
    'Instrument-to-account currency rate at placement. Used for the funds check and the reservation of a NEW order.';
COMMENT ON COLUMN orders.fill_fx_rate IS
    'Instrument-to-account currency rate fetched by the executor at fill. Rate of record for settlement, fee and realised P&L. NULL until filled.';
COMMENT ON COLUMN orders.realised_pnl IS
    'Realised P&L in account currency, booked when a SELL fills: quantity * (executed_price * fill_fx_rate - positions.average_cost_account). NULL for BUYs and unfilled orders.';
COMMENT ON COLUMN orders.fee IS
    'Transaction fee in account currency, calculated on the account-currency notional.';

-- Average cost in account currency, built from each fill at its fill rate, so unrealised and
-- realised P&L in account currency include the effect of FX moves since purchase.
ALTER TABLE positions ADD COLUMN average_cost_account NUMERIC(18, 6) NOT NULL DEFAULT 0;
UPDATE positions SET average_cost_account = average_cost;

COMMENT ON COLUMN positions.average_cost IS
    'Average cost per unit in the instrument''s currency.';
COMMENT ON COLUMN positions.average_cost_account IS
    'Average cost per unit in the account''s currency, at the fill rates of the buys that built the position.';

ALTER TABLE accounts    ADD CONSTRAINT ck_accounts_currency_iso    CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE instruments ADD CONSTRAINT ck_instruments_currency_iso CHECK (currency ~ '^[A-Z]{3}$');

COMMENT ON COLUMN assets.flat_fee IS
    'Fixed fee per trade, in account currency (all accounts are USD).';
