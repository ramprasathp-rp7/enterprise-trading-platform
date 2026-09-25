-- Seed asset fee structures for each asset class
-- Fee Calculation: total_fee = flat_fee + (notional * percentage_fee)
-- Note: percentage_fee is stored in decimal form (e.g., 0.0001 = 0.01%)

INSERT INTO assets (asset_type, flat_fee, percentage_fee, updated_on)
VALUES
    ('EQUITY',   5.00,  0.0001, TIMESTAMP '2026-01-01 00:00:00+00'),  -- $5 flat + 0.01% of notional
    ('FX',       2.50,  0.00005, TIMESTAMP '2026-01-01 00:00:00+00'),  -- $2.50 flat + 0.005% of notional
    ('CRYPTO',  10.00,  0.0002, TIMESTAMP '2026-01-01 00:00:00+00');  -- $10 flat + 0.02% of notional

COMMENT ON TABLE assets IS 'Fee structures seeded for production use.';
