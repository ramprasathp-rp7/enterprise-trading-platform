-- Migration 015: Drop the legacy accounts.preferences column
--
-- Migration 006 added a nullable preferences JSONB column to accounts for an earlier, generic
-- key/value preferences feature. That feature was removed in Sprint 10 and replaced by the
-- preferences module's own table, customer_preferences (migration 014).
-- See docs/sprint-10/decision-log/0001-preferences-own-table-replacing-accounts-jsonb.md.
--
-- Nothing reads or writes the column: the order-service and executor AccountMappers and the
-- analytics extract all select named columns. IF EXISTS keeps this safe on a database where the
-- column was never created.

ALTER TABLE accounts
    DROP COLUMN IF EXISTS preferences;
