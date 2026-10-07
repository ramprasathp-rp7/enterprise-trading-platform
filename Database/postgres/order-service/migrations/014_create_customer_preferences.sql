-- Migration 014: Customer preferences module table (Sprint 10)
--
-- Owned by the preferences module (com.tradingplatform.preferences). Nothing outside that module
-- reads or writes this table; other modules resolve channels through the PreferenceResolver
-- Java interface instead.
--
-- Design:
-- - One row per (account, alert channel). A customer may choose several channels (EMAIL, SMS,
--   IN_APP) but holds at most one contact detail per channel; several addresses for one channel
--   is out of scope in the brief.
-- - One user holds exactly one account (auth.users.account_id is UNIQUE), so the account is the
--   customer's identity on this side of the platform.
-- - No rows for an account means the customer has not chosen: the platform default (IN_APP)
--   applies. The application never leaves an account with zero rows after a save; a save
--   replaces the account's rows inside one transaction.
-- - contact_detail_enc holds the email address or E.164 number encrypted with AES-256-GCM
--   (12-byte IV followed by ciphertext and tag), bound to the account id. No other table on the
--   platform holds an email or phone number, so this is the only copy; it is never stored in clear.
-- - IN_APP needs no contact detail; EMAIL and SMS always have one (ck_cp_contact).
-- - updated_at is set by the application on every write rather than by a plpgsql trigger, so the
--   same file runs unchanged against H2 in the module's restart test.
--
-- Supersedes accounts.preferences (migration 006), which migration 015 drops.

CREATE TABLE customer_preferences (
    account_id          BIGINT                   NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    alert_channel       VARCHAR(16)              NOT NULL,
    contact_detail_enc  BYTEA,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_preferences PRIMARY KEY (account_id, alert_channel),
    CONSTRAINT ck_cp_channel CHECK (alert_channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT ck_cp_contact CHECK ((alert_channel = 'IN_APP') = (contact_detail_enc IS NULL))
);

COMMENT ON TABLE customer_preferences IS
    'Sprint 10 preferences module. One row per account and chosen alert channel; no rows means the IN_APP platform default.';
COMMENT ON COLUMN customer_preferences.contact_detail_enc IS
    'AES-256-GCM ciphertext (iv || ct+tag) of the email or E.164 number, AAD = account id. NULL for IN_APP.';
