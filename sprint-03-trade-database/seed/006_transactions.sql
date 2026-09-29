-- Example seed data for transactions table
-- Assumes accounts and orders seeds have already been loaded.

-- Simple deposit for ACC-000001
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), a.id, NULL, 'DEPOSIT', 'CREDIT', 500.00, TIMESTAMP '2026-01-11 10:00:00+00'
FROM accounts a WHERE a.account_id = 'ACC-000001';

-- Simple withdrawal for ACC-000002
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), a.id, NULL, 'WITHDRAW', 'DEBIT', 50.00, TIMESTAMP '2026-02-04 09:00:00+00'
FROM accounts a WHERE a.account_id = 'ACC-000002';

-- Fee charged to ACC-000005
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), o.account_id, o.id, 'FEE', 'DEBIT', 12.50, TIMESTAMP '2026-02-14 11:00:00+00'
FROM orders o WHERE o.idempotency_key = 'SEED-0001-A' AND o.status = 'FILLED';

-- BUY transaction derived from a filled order (SEED-0001-A)
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), o.account_id, o.id, 'BUY', 'DEBIT', (o.executed_price * o.quantity), o.updated_on
FROM orders o WHERE o.idempotency_key = 'SEED-0001-A' AND o.status = 'FILLED';

-- SELL transaction derived from a filled sell order (SEED-0005-C)
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), o.account_id, o.id, 'SELL', 'CREDIT', (o.executed_price * o.quantity), o.updated_on
FROM orders o WHERE o.idempotency_key = 'SEED-0005-C' AND o.status = 'FILLED';

-- Deposit for ACC-000006
INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on)
SELECT gen_random_uuid(), a.id, NULL, 'DEPOSIT', 'CREDIT', 2500.00, TIMESTAMP '2026-03-11 08:10:00+00'
FROM accounts a WHERE a.account_id = 'ACC-000006';
