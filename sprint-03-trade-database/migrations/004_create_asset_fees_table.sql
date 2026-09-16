-- Create asset_fees table to manage fee structure by asset class
CREATE TABLE asset_fees (
    asset_type       VARCHAR(10)      PRIMARY KEY,
    flat_fee         NUMERIC(18, 2)   NOT NULL DEFAULT 0,
    percentage_fee   NUMERIC(18, 6)   NOT NULL DEFAULT 0,
    updated_on       TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT ck_asset_fees_flat_non_negative CHECK (flat_fee >= 0),
    CONSTRAINT ck_asset_fees_percentage_non_negative CHECK (percentage_fee >= 0),
    CONSTRAINT ck_asset_fees_at_least_one CHECK (flat_fee > 0 OR percentage_fee > 0)
);

COMMENT ON TABLE asset_fees IS
    'Fee structure by asset class. Fees are calculated as: flat_fee + (trade_volume * percentage_fee / 100). '
    'If one component is 0, the other is the sole contributor. At least one must be > 0.';

COMMENT ON COLUMN asset_fees.asset_type IS
    'Asset class from instruments.asset_class: EQUITY, FX, or CRYPTO.';

COMMENT ON COLUMN asset_fees.flat_fee IS
    'Fixed fee per trade, in base currency.';

COMMENT ON COLUMN asset_fees.percentage_fee IS
    'Percentage fee on trade notional value in decimal form. E.g., 0.0001 means 0.01%.';

COMMENT ON COLUMN asset_fees.updated_on IS
    'Timestamp when fee structure was last modified. Used for tracking changes.';
