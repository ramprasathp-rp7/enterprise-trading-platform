-- Probe: Orphan foreign key check
-- Expected SQLSTATE: 23503 (foreign_key_violation)

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
    999999, -- Non-existent parent account ID
    i.id, 
    'BUY', 
    10, 
    25.00, 
    25.00, 
    'FILLED', 
    'PROBE-ORPHAN-KEY-001', 
    now(), 
    now()
FROM instruments i 
WHERE i.symbol = 'ACME';