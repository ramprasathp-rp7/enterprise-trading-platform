-- Create assets table to manage fee structure by asset class
CREATE TABLE assets (
    asset_type       VARCHAR(10)      PRIMARY KEY,
    flat_fee         NUMERIC(18, 2)   NOT NULL DEFAULT 0,
    percentage_fee   NUMERIC(18, 6)   NOT NULL DEFAULT 0,
    updated_on       TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT ck_assets_flat_non_negative CHECK (flat_fee >= 0),
    CONSTRAINT ck_assets_percentage_non_negative CHECK (percentage_fee >= 0),
    CONSTRAINT ck_assets_at_least_one CHECK (flat_fee > 0 OR percentage_fee > 0)
);

COMMENT ON TABLE assets IS
    'Fee structure by asset class. Fees are calculated as: flat_fee + (trade_volume * percentage_fee / 100). '
    'If one component is 0, the other is the sole contributor. At least one must be > 0.';

COMMENT ON COLUMN assets.asset_type IS
    'Asset class from instruments.asset_class: EQUITY, FX, or CRYPTO.';

COMMENT ON COLUMN assets.flat_fee IS
    'Fixed fee per trade, in base currency.';

COMMENT ON COLUMN assets.percentage_fee IS
    'Percentage fee on trade notional value in decimal form. E.g., 0.0001 means 0.01%.';

COMMENT ON COLUMN assets.updated_on IS
    'Timestamp when fee structure was last modified. Used for tracking changes.';


-- Add FK from instruments.asset_class -> assets.asset_type without immediate validation
-- This avoids migration failures if there is legacy data to fix first.
ALTER TABLE instruments
    ADD CONSTRAINT fk_instruments_asset_class
    FOREIGN KEY (asset_class) REFERENCES assets(asset_type) ON DELETE RESTRICT NOT VALID;

-- To enforce fully, run the following after confirming data integrity:
-- ALTER TABLE instruments VALIDATE CONSTRAINT fk_instruments_asset_class;
