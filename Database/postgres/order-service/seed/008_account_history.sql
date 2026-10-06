-- Account History Snapshots (daily end-of-day, 23:59:59 UTC)
-- Seed data for account_history table.
-- networth = cash_balance + market value of the account's seeded positions (005_positions.sql),
-- valued around average cost with small daily moves, so the first real scheduler run lines up.
-- Accounts are looked up by account number, matching the other seed files.

-- ACC-000001 (Priya Menon) - ACTIVE - cash 24500.75; 100 ACME + 40 INFY.NS (cost 65760.00)
INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
SELECT a.id, v.snapshot_date::timestamptz, v.cash_balance, v.networth, now()
FROM accounts a
CROSS JOIN (VALUES
    ('2026-09-28 23:59:59+00', 24500.75, 90260.75),
    ('2026-09-29 23:59:59+00', 24500.75, 90523.79),
    ('2026-09-30 23:59:59+00', 24500.75, 90063.47),
    ('2026-10-01 23:59:59+00', 24500.75, 90786.83),
    ('2026-10-02 23:59:59+00', 24500.75, 91049.87),
    ('2026-10-03 23:59:59+00', 24500.75, 90852.59),
    ('2026-10-04 23:59:59+00', 24500.75, 91247.15),
    ('2026-10-05 23:59:59+00', 24500.75, 91444.43)
) AS v(snapshot_date, cash_balance, networth)
WHERE a.account_id = 'ACC-000001';

-- ACC-000002 (Rahul Sharma) - ACTIVE - cash 100.00; no positions
INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
SELECT a.id, v.snapshot_date::timestamptz, v.cash_balance, v.networth, now()
FROM accounts a
CROSS JOIN (VALUES
    ('2026-09-28 23:59:59+00', 100.00, 100.00),
    ('2026-09-29 23:59:59+00', 100.00, 100.00),
    ('2026-09-30 23:59:59+00', 100.00, 100.00),
    ('2026-10-01 23:59:59+00', 100.00, 100.00),
    ('2026-10-02 23:59:59+00', 100.00, 100.00),
    ('2026-10-03 23:59:59+00', 100.00, 100.00),
    ('2026-10-04 23:59:59+00', 100.00, 100.00),
    ('2026-10-05 23:59:59+00', 100.00, 100.00)
) AS v(snapshot_date, cash_balance, networth)
WHERE a.account_id = 'ACC-000002';

-- ACC-000005 (Meera Nair) - ACTIVE - cash 15000.00; 200 TATASTEEL.BO + 30 RELIANCE.NS (cost 101150.00)
INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
SELECT a.id, v.snapshot_date::timestamptz, v.cash_balance, v.networth, now()
FROM accounts a
CROSS JOIN (VALUES
    ('2026-09-28 23:59:59+00', 15000.00, 116150.00),
    ('2026-09-29 23:59:59+00', 15000.00, 116554.60),
    ('2026-09-30 23:59:59+00', 15000.00, 115846.55),
    ('2026-10-01 23:59:59+00', 15000.00, 116959.20),
    ('2026-10-02 23:59:59+00', 15000.00, 117363.80),
    ('2026-10-03 23:59:59+00', 15000.00, 117060.35),
    ('2026-10-04 23:59:59+00', 15000.00, 117667.25),
    ('2026-10-05 23:59:59+00', 15000.00, 117970.70)
) AS v(snapshot_date, cash_balance, networth)
WHERE a.account_id = 'ACC-000005';

-- ACC-000006 (Arjun Rao) - ACTIVE - cash 8000.00; 1000 FX:EURUSD (cost 925.00)
INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
SELECT a.id, v.snapshot_date::timestamptz, v.cash_balance, v.networth, now()
FROM accounts a
CROSS JOIN (VALUES
    ('2026-09-28 23:59:59+00', 8000.00, 8925.00),
    ('2026-09-29 23:59:59+00', 8000.00, 8928.70),
    ('2026-09-30 23:59:59+00', 8000.00, 8922.23),
    ('2026-10-01 23:59:59+00', 8000.00, 8932.40),
    ('2026-10-02 23:59:59+00', 8000.00, 8936.10),
    ('2026-10-03 23:59:59+00', 8000.00, 8933.33),
    ('2026-10-04 23:59:59+00', 8000.00, 8938.88),
    ('2026-10-05 23:59:59+00', 8000.00, 8941.65)
) AS v(snapshot_date, cash_balance, networth)
WHERE a.account_id = 'ACC-000006';

-- ACC-000003 (Ananya Iyer) - SUSPENDED 2026-03-01 - kept for compliance/audit; cash 5000.00, no positions
INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
SELECT a.id, v.snapshot_date::timestamptz, v.cash_balance, v.networth, now()
FROM accounts a
CROSS JOIN (VALUES
    ('2026-02-22 23:59:59+00', 5000.00, 5000.00),
    ('2026-02-23 23:59:59+00', 5000.00, 5000.00),
    ('2026-02-24 23:59:59+00', 5000.00, 5000.00),
    ('2026-02-25 23:59:59+00', 5000.00, 5000.00),
    ('2026-02-26 23:59:59+00', 5000.00, 5000.00),
    ('2026-02-27 23:59:59+00', 5000.00, 5000.00),
    ('2026-02-28 23:59:59+00', 5000.00, 5000.00),
    ('2026-03-01 23:59:59+00', 5000.00, 5000.00)
) AS v(snapshot_date, cash_balance, networth)
WHERE a.account_id = 'ACC-000003';

-- ACC-000004 (Vikram Desai) - CLOSED 2026-06-05 - kept for compliance/audit; cash 0.00, AAPL position fully sold
INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
SELECT a.id, v.snapshot_date::timestamptz, v.cash_balance, v.networth, now()
FROM accounts a
CROSS JOIN (VALUES
    ('2026-05-29 23:59:59+00', 0.00, 0.00),
    ('2026-05-30 23:59:59+00', 0.00, 0.00),
    ('2026-05-31 23:59:59+00', 0.00, 0.00),
    ('2026-06-01 23:59:59+00', 0.00, 0.00),
    ('2026-06-02 23:59:59+00', 0.00, 0.00),
    ('2026-06-03 23:59:59+00', 0.00, 0.00),
    ('2026-06-04 23:59:59+00', 0.00, 0.00),
    ('2026-06-05 23:59:59+00', 0.00, 0.00)
) AS v(snapshot_date, cash_balance, networth)
WHERE a.account_id = 'ACC-000004';

