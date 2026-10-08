-- Positions built by the filled orders in 004_orders.sql (quantity, average cost in the instrument's
-- currency, last_updated = the last fill):
--   ACC-000001 PFE          100 = SEED-0001-A 60 @ 25.00 + SEED-0001-B 40 @ 26.25 -> avg 25.50
--   ACC-000001 INFY.NS       40 = SEED-0001-C @ 1580.25
--   ACC-000004 AAPL           0 = SEED-0004-A buy 20 @ 180.00, SEED-0004-B sell 20 (average cost kept)
--   ACC-000005 TATASTEEL.BO 200 = SEED-0005-A @ 145.75
--   ACC-000005 RELIANCE.NS   30 = SEED-0005-B buy 50 @ 2400.00, SEED-0005-C sell 20
--   ACC-000006 FX:EURUSD   1000 = SEED-0006-A @ 0.93
-- Cancelled, rejected and NEW orders hold nothing.

INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
SELECT a.id, i.id, 100, 25.50, TIMESTAMP '2026-01-20 14:05:04+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000001' AND i.symbol = 'PFE';

INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
SELECT a.id, i.id, 40, 1580.25, TIMESTAMP '2026-02-05 10:00:03+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000001' AND i.symbol = 'INFY.NS';

INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
SELECT a.id, i.id, 0, 180.00, TIMESTAMP '2026-04-02 11:30:02+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000004' AND i.symbol = 'AAPL';

INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
SELECT a.id, i.id, 200, 145.75, TIMESTAMP '2026-02-14 10:45:03+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000005' AND i.symbol = 'TATASTEEL.BO';

INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
SELECT a.id, i.id, 30, 2400.00, TIMESTAMP '2026-05-20 13:15:02+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000005' AND i.symbol = 'RELIANCE.NS';

INSERT INTO positions (account_id, instrument_id, quantity, average_cost, last_updated)
SELECT a.id, i.id, 1000, 0.930000, TIMESTAMP '2026-03-10 08:05:01+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000006' AND i.symbol = 'FX:EURUSD';

-- trg_positions_set_updated would stamp last_updated with the seeding time; keep the last-fill times.
ALTER TABLE positions DISABLE TRIGGER trg_positions_set_updated;

-- Average cost in account currency (USD). USD instruments match average_cost; INR holdings are
-- converted at the seed reference FX:USDINR = 83.47 (see 004_orders.sql).
UPDATE positions SET average_cost_account = average_cost;

UPDATE positions p
SET average_cost_account = ROUND(p.average_cost * 0.0119803522, 6)
FROM instruments i
WHERE i.id = p.instrument_id AND i.currency = 'INR';

ALTER TABLE positions ENABLE TRIGGER trg_positions_set_updated;
