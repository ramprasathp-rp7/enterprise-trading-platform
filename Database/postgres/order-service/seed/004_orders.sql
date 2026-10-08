INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 60, 25.00, 25.00, 'FILLED', 'SEED-0001-A', TIMESTAMP '2026-01-12 09:30:00+00', TIMESTAMP '2026-01-12 09:30:05+00', TIMESTAMP '2026-01-12 09:30:05+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000001' AND i.symbol = 'PFE';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 40, 26.25, 26.25, 'FILLED', 'SEED-0001-B', TIMESTAMP '2026-01-20 14:05:00+00', TIMESTAMP '2026-01-20 14:05:04+00', TIMESTAMP '2026-01-20 14:05:04+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000001' AND i.symbol = 'PFE';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 40, 1580.25, 1580.25, 'FILLED', 'SEED-0001-C', TIMESTAMP '2026-02-05 10:00:00+00', TIMESTAMP '2026-02-05 10:00:03+00', TIMESTAMP '2026-02-05 10:00:03+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000001' AND i.symbol = 'INFY.NS';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason, cancelled_on)
SELECT a.id, i.id, 'BUY', 10, 190.00, NULL, 'CANCELLED', 'SEED-0001-D', TIMESTAMP '2026-03-10 16:20:00+00', TIMESTAMP '2026-03-10 16:45:00+00', NULL, NULL, TIMESTAMP '2026-03-10 16:45:00+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000001' AND i.symbol = 'AAPL';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 50, 25.50, NULL, 'REJECTED', 'SEED-0002-A', TIMESTAMP '2026-02-10 12:00:00+00', TIMESTAMP '2026-02-10 12:00:01+00', TIMESTAMP '2026-02-10 12:00:01+00', 'INSUFFICIENT_FUNDS'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000002' AND i.symbol = 'PFE';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 20, 180.00, 180.00, 'FILLED', 'SEED-0004-A', TIMESTAMP '2026-01-18 09:15:00+00', TIMESTAMP '2026-01-18 09:15:02+00', TIMESTAMP '2026-01-18 09:15:02+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000004' AND i.symbol = 'AAPL';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'SELL', 20, 185.00, 185.00, 'FILLED', 'SEED-0004-B', TIMESTAMP '2026-04-02 11:30:00+00', TIMESTAMP '2026-04-02 11:30:02+00', TIMESTAMP '2026-04-02 11:30:02+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000004' AND i.symbol = 'AAPL';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason, cancelled_on)
SELECT a.id, i.id, 'BUY', 10, 140.00, NULL, 'CANCELLED', 'SEED-0004-C', TIMESTAMP '2026-05-01 15:00:00+00', TIMESTAMP '2026-05-01 15:10:00+00', NULL, NULL, TIMESTAMP '2026-05-01 15:10:00+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000004' AND i.symbol = 'TATASTEEL.BO';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 200, 145.75, 145.75, 'FILLED', 'SEED-0005-A', TIMESTAMP '2026-02-14 10:45:00+00', TIMESTAMP '2026-02-14 10:45:03+00', TIMESTAMP '2026-02-14 10:45:03+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000005' AND i.symbol = 'TATASTEEL.BO';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 50, 2400.00, 2400.00, 'FILLED', 'SEED-0005-B', TIMESTAMP '2026-03-01 09:50:00+00', TIMESTAMP '2026-03-01 09:50:02+00', TIMESTAMP '2026-03-01 09:50:02+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000005' AND i.symbol = 'RELIANCE.NS';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'SELL', 20, 2500.00, 2500.00, 'FILLED', 'SEED-0005-C', TIMESTAMP '2026-05-20 13:15:00+00', TIMESTAMP '2026-05-20 13:15:02+00', TIMESTAMP '2026-05-20 13:15:02+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000005' AND i.symbol = 'RELIANCE.NS';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 1000, 0.93, 0.93, 'FILLED', 'SEED-0006-A', TIMESTAMP '2026-03-10 08:05:00+00', TIMESTAMP '2026-03-10 08:05:01+00', TIMESTAMP '2026-03-10 08:05:01+00', NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000006' AND i.symbol = 'FX:EURUSD';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'SELL', 10, 2500.00, NULL, 'REJECTED', 'SEED-0006-B', TIMESTAMP '2026-06-01 10:20:00+00', TIMESTAMP '2026-06-01 10:20:01+00', TIMESTAMP '2026-06-01 10:20:01+00', 'PRICE_NOT_MET'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000006' AND i.symbol = 'RELIANCE.NS';

INSERT INTO orders (account_id, instrument_id, side, quantity, price, executed_price, status, idempotency_key, created_on, updated_on, executed_on, rejected_reason)
SELECT a.id, i.id, 'BUY', 25, 1600.00, NULL, 'NEW', 'SEED-0006-C', TIMESTAMP '2026-08-20 09:05:00+00', TIMESTAMP '2026-08-20 09:05:00+00', NULL, NULL
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000006' AND i.symbol = 'INFY.NS';

-- The updates below fill in columns of rows inserted above. trg_orders_set_updated would stamp
-- updated_on with the seeding time, so it is off until they are done and the seeded timestamps stand.
ALTER TABLE orders DISABLE TRIGGER trg_orders_set_updated;

-- Multi-currency (migration 022). Rates are account (USD) per instrument-currency unit, at the seed
-- reference FX:USDINR = 83.47, so INR -> USD = 1 / 83.47. USD instruments keep the default rate of 1.
-- Seeded orders filled at the rate they were placed at.
UPDATE orders o
SET placement_fx_rate = 0.0119803522
FROM instruments i
WHERE i.id = o.instrument_id AND i.currency = 'INR' AND o.idempotency_key LIKE 'SEED-%';

UPDATE orders
SET fill_fx_rate = placement_fx_rate
WHERE status = 'FILLED' AND idempotency_key LIKE 'SEED-%';

-- Realised P&L booked on the filled SELLs, in USD:
--   SEED-0004-B: 20 AAPL  * (185.00 - 180.00)                               = 100.00
--   SEED-0005-C: 20 RELIANCE.NS * (2500.00 * 0.0119803522 - 28.752845)      =  23.96
UPDATE orders SET realised_pnl = 100.00 WHERE idempotency_key = 'SEED-0004-B';
UPDATE orders SET realised_pnl = 23.96  WHERE idempotency_key = 'SEED-0005-C';

-- Fees on the filled orders, in USD, as FeeDeductionService computes them: flat + percentage of the
-- account-currency notional (002_asset_fees.sql: EQUITY 5.00 + 0.01%, FX 2.50 + 0.005%), rounded half up.
-- 006_transactions.sql books the same amounts as FEE transactions.
UPDATE orders o
SET fee = v.fee
FROM (VALUES
    ('SEED-0001-A', 5.15),  -- 60 PFE  * 25.00                    = 1500.00
    ('SEED-0001-B', 5.11),  -- 40 PFE  * 26.25                    = 1050.00
    ('SEED-0001-C', 5.08),  -- 40 INFY.NS * 1580.25 INR           =  757.28
    ('SEED-0004-A', 5.36),  -- 20 AAPL * 180.00                   = 3600.00
    ('SEED-0004-B', 5.37),  -- 20 AAPL * 185.00                   = 3700.00
    ('SEED-0005-A', 5.03),  -- 200 TATASTEEL.BO * 145.75 INR      =  349.23
    ('SEED-0005-B', 5.14),  -- 50 RELIANCE.NS * 2400.00 INR       = 1437.64
    ('SEED-0005-C', 5.06),  -- 20 RELIANCE.NS * 2500.00 INR       =  599.02
    ('SEED-0006-A', 2.55)   -- 1000 FX:EURUSD * 0.93              =  930.00
) AS v(idempotency_key, fee)
WHERE o.idempotency_key = v.idempotency_key;

ALTER TABLE orders ENABLE TRIGGER trg_orders_set_updated;
