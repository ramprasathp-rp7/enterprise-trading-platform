-- Migration 003: Create Customer Preferences and Platform Defaults Tables

-- Create customer_preferences table
CREATE TABLE IF NOT EXISTS customer_preferences (
    account_id BIGINT NOT NULL PRIMARY KEY,
    preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_preferences_account 
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- Create index on account_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_customer_preferences_account_id 
    ON customer_preferences(account_id);

-- Create platform_defaults table for system-wide defaults
CREATE TABLE IF NOT EXISTS platform_defaults (
    id BIGINT NOT NULL PRIMARY KEY DEFAULT 1,
    defaults JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_defaults_single_row CHECK (id = 1)
);

-- Insert initial platform defaults if not exists
INSERT INTO platform_defaults (id, defaults, last_updated) 
VALUES (
    1,
    '{
        "NOTIFICATION_EMAIL": true,
        "NOTIFICATION_TRADING_ALERTS": true,
        "DISPLAY_THEME": "light",
        "DISPLAY_CURRENCY": "USD",
        "TRADING_DEFAULT_ORDER_TYPE": "LIMIT",
        "TRADING_MAX_ORDER_SIZE": 10000,
        "TIMEZONE": "UTC",
        "LANGUAGE": "en"
    }'::jsonb,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Create audit comment
COMMENT ON TABLE customer_preferences IS 'Stores per-account user preferences as JSONB. Overrides platform defaults when merged.';
COMMENT ON TABLE platform_defaults IS 'Stores system-wide default preferences. Single row (id=1) enforced by CHECK constraint.';
COMMENT ON COLUMN customer_preferences.preferences IS 'JSON object with preference keys and values. Merged with platform_defaults on retrieval.';
