-- Migration 024: Admin preferences (Sprint 10)
--
-- Owned by the preferences module (com.tradingplatform.preferences), like customer_preferences
-- (014). Nothing outside that module reads or writes this table; other modules resolve an admin's
-- channels through the AdminPreferenceResolver Java interface.
--
-- Design:
-- - Admins have no trading account of their own. The seeded admin holds operator account 0, which
--   is not a row in accounts, and every admin would share it. So rows are keyed by the admin's
--   user id instead: the JWT sub claim, the auth service's auth.users.id (a UUID).
-- - Otherwise the same shape and rules as customer_preferences: one row per (admin, channel), no
--   rows means the IN_APP platform default, a save replaces the admin's rows in one transaction.
-- - contact_detail_enc is AES-256-GCM ciphertext bound to the admin user id. NULL for IN_APP.

CREATE TABLE admin_preferences (
    admin_user_id       VARCHAR(64)              NOT NULL,
    alert_channel       VARCHAR(16)              NOT NULL,
    contact_detail_enc  BYTEA,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_admin_preferences PRIMARY KEY (admin_user_id, alert_channel),
    CONSTRAINT ck_ap_channel CHECK (alert_channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT ck_ap_contact CHECK ((alert_channel = 'IN_APP') = (contact_detail_enc IS NULL))
);

COMMENT ON TABLE admin_preferences IS
    'Sprint 10 preferences module. One row per admin user and chosen alert channel; no rows means the IN_APP platform default.';
COMMENT ON COLUMN admin_preferences.admin_user_id IS
    'The admin''s auth.users.id (JWT sub claim). Admins have no trading account to key on.';
COMMENT ON COLUMN admin_preferences.contact_detail_enc IS
    'AES-256-GCM ciphertext (iv || ct+tag) of the email or E.164 number, AAD = admin user id. NULL for IN_APP.';
