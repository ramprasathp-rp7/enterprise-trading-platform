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
SELECT a.id, i.id, 1000, 0.925000, TIMESTAMP '2026-03-10 08:05:01+00'
FROM accounts a, instruments i WHERE a.account_id = 'ACC-000006' AND i.symbol = 'FX:EURUSD';

-- Average cost in account currency (USD). USD instruments match average_cost; INR holdings are
-- converted at the seed reference FX:USDINR = 83.47 (see 004_orders.sql).
UPDATE positions SET average_cost_account = average_cost;

UPDATE positions p
SET average_cost_account = ROUND(p.average_cost * 0.0119803522, 6)
FROM instruments i
WHERE i.id = p.instrument_id AND i.currency = 'INR';
