EXPLAIN ANALYZE
SELECT * FROM orders WHERE account_id = 6 AND status = 'NEW' ORDER BY created_on DESC;

EXPLAIN ANALYZE
SELECT * FROM orders WHERE account_id = 6 ORDER BY created_on DESC LIMIT 50;

EXPLAIN ANALYZE
SELECT instrument_id, quantity, average_cost, last_updated FROM positions WHERE account_id = 6;

EXPLAIN ANALYZE
SELECT * FROM orders WHERE created_on >= '2026-03-10 08:05:01+00';

EXPLAIN ANALYZE
SELECT * FROM accounts WHERE account_id = 'ACC-000006';

EXPLAIN ANALYZE
SELECT 
    id,
    instrument_id,
    quantity,
    executed_price,
    created_on,
    -- Running total of cash committed to date
    SUM(quantity * executed_price) OVER (
        ORDER BY created_on ASC 
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_cash_committed,
    -- Rank by transaction value within its instrument
    DENSE_RANK() OVER (
        PARTITION BY instrument_id 
        ORDER BY (quantity * executed_price) DESC
    ) AS value_rank_in_instrument
FROM orders
WHERE account_id = 6 
  AND status = 'FILLED'
ORDER BY created_on ASC;
