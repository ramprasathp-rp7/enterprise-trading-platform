-- Migration 006: Add Customer Preferences Column to Accounts Table
--
-- SUPERSEDED: this column is dropped by 015_drop_accounts_preferences.sql. Customer preferences
-- now live in customer_preferences (014), owned by the Sprint 10 preferences module. Kept so the
-- migration history replays in order on a fresh database.
--
-- This migration adds a nullable preferences JSONB column to the accounts table.
-- 
-- Design:
-- - preferences column is NULLABLE (NULL means account uses platform defaults)
-- - When preferences is NULL, application uses Spring configuration defaults
-- - When preferences is NOT NULL, contains per-account overrides to be merged with defaults
-- - This approach is space-efficient and allows for environment-specific default changes
--   via Spring configuration without database migrations

ALTER TABLE accounts
    ADD COLUMN preferences JSONB;

-- Add comment explaining the column behavior
COMMENT ON COLUMN accounts.preferences IS 
    'Per-account preferences as JSONB. NULL means inherit all defaults from Spring config. '
    'Non-NULL contains partial or full preference overrides to be merged with defaults. '
    'Application layer handles merge: property defaults + column values (column wins on conflicts).';
