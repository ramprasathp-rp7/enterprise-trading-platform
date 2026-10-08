-- Cash ledger matching 001_accounts.sql and 004_orders.sql.
--
-- Every FILLED seed order has what a real fill records (FeeDeductionService):
--   - a BUY (DEBIT) or SELL (CREDIT) for the notional in account currency, at the fill time:
--     ROUND(executed_price * quantity * fill_fx_rate, 2)
--   - a FEE (DEBIT) for orders.fee, a second later.
-- Cancelled, rejected and NEW orders move no cash.
--
-- Opening deposits (and ACC-000004's closing withdrawal) are sized so each account's ledger,
-- credits minus debits, equals its cash_balance:
--   ACC-000001  27823.37 - (1500.00 + 5.15) - (1050.00 + 5.11) - (757.28 + 5.08)                  = 24500.75
--   ACC-000002    150.00 - 50.00 (withdrawal)                                                       =   100.00
--   ACC-000003   5000.00                                                                           =  5000.00
--   ACC-000004   5000.00 - (3600.00 + 5.36) + (3700.00 - 5.37) - 5089.27 (withdrawal on closing)   =     0.00
--   ACC-000005  16203.08 - (349.23 + 5.03) - (1437.64 + 5.14) + (599.02 - 5.06)                    = 15000.00
--   ACC-000006   8932.55 - (930.00 + 2.55)                                                         =  8000.00

-- Deposits and withdrawals
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), a.id, NULL, v.transaction_type, v.action_type, v.amount, v.created_on::timestamptz
FROM accounts a
JOIN (VALUES
    ('ACC-000001', 'DEPOSIT',  'CREDIT', 27823.37, '2026-01-10 09:05:00+00'),
    ('ACC-000002', 'DEPOSIT',  'CREDIT',   150.00, '2026-02-03 11:20:00+00'),
    ('ACC-000002', 'WITHDRAW', 'DEBIT',     50.00, '2026-02-04 09:00:00+00'),
    ('ACC-000003', 'DEPOSIT',  'CREDIT',  5000.00, '2026-02-20 10:35:00+00'),
    ('ACC-000004', 'DEPOSIT',  'CREDIT',  5000.00, '2026-01-15 09:50:00+00'),
    ('ACC-000004', 'WITHDRAW', 'DEBIT',   5089.27, '2026-06-05 15:55:00+00'),
    ('ACC-000005', 'DEPOSIT',  'CREDIT', 16203.08, '2026-01-25 09:25:00+00'),
    ('ACC-000006', 'DEPOSIT',  'CREDIT',  8932.55, '2026-03-05 13:45:00+00')
) AS v(account_number, transaction_type, action_type, amount, created_on)
  ON a.account_id = v.account_number;

-- Settlement of every filled seed order
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), o.account_id, o.id, o.side,
       CASE o.side WHEN 'BUY' THEN 'DEBIT' ELSE 'CREDIT' END,
       ROUND(o.executed_price * o.quantity * o.fill_fx_rate, 2),
       o.executed_on
FROM orders o
WHERE o.idempotency_key LIKE 'SEED-%' AND o.status = 'FILLED';

-- Fee of every filled seed order
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), o.account_id, o.id, 'FEE', 'DEBIT', o.fee, o.executed_on + INTERVAL '1 second'
FROM orders o
WHERE o.idempotency_key LIKE 'SEED-%' AND o.status = 'FILLED' AND o.fee > 0;
