-- Probe: Duplicate idempotency key check
-- Expected SQLSTATE: 23505 (unique_violation)

INSERT INTO orders (
    account_id,
    instrument_id,
    side,
    quantity,
    price,
    executed_price,
    status,
    idempotency_key,
    created_on,
    updated_on
)
SELECT 
    a.id, 
    i.id, 
    'BUY', 
    10, 
    25.00, 
    25.00, 
    'FILLED', 
    'SEED-0001-A', -- Duplicate key from 003_orders.sql
    now(), 
    now()
FROM accounts a, instruments i 
WHERE a.account_id = 'ACC-000001' AND i.symbol = 'ACME';