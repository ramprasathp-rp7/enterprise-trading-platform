CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.known_account_ids (
    account_id  BIGINT       PRIMARY KEY,
    created_on  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE auth.known_account_ids IS
    'Mirrors accounts.id from the trade database, for existence checks only. '
    'Seeded once alongside the trade DB seed data, not synced at runtime. '
    'A row here does not imply anything about the account''s current status, '
    'cash balance, or holder — only that the id was valid as of seeding.';

CREATE TABLE IF NOT EXISTS auth.users (
    id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(64)     NOT NULL,
    password_hash  VARCHAR(255)    NOT NULL,
    account_id     BIGINT          NOT NULL,
    roles          TEXT[]          NOT NULL DEFAULT ARRAY['CUSTOMER'],
    created_on     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_updated   TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_account_id UNIQUE (account_id),
    CONSTRAINT fk_users_account_id FOREIGN KEY (account_id) REFERENCES auth.known_account_ids(account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_users_username_pattern CHECK (username ~ '^[a-zA-Z0-9._-]+$'),
    CONSTRAINT ck_users_username_length CHECK (char_length(username) BETWEEN 3 AND 64),
    CONSTRAINT ck_users_roles_non_empty CHECK (array_length(roles, 1) >= 1),
    CONSTRAINT ck_users_roles_valid CHECK (roles <@ ARRAY['CUSTOMER', 'ADMIN']::TEXT[])
);

COMMENT ON TABLE auth.users IS
    'Identities owned by the auth service, in the auth service''s own database. '
    'Never deleted, to keep refresh_tokens and audit history meaningful — '
    'deactivate rather than remove if that need arises later.';

CREATE OR REPLACE FUNCTION auth.set_users_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_updated
    BEFORE UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.set_users_updated_timestamp();

CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID          NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255)  NOT NULL,
    family_id    UUID          NOT NULL,
    issued_on    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_on   TIMESTAMPTZ   NOT NULL,
    revoked_on   TIMESTAMPTZ,
    replaced_by  UUID          REFERENCES auth.refresh_tokens(id),

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

COMMENT ON TABLE auth.refresh_tokens IS
    'One row per refresh token ever issued, including consumed and revoked '
    'ones — the record refresh-reuse detection needs. A row is never deleted, '
    'only marked revoked_on, so a stolen-and-replayed token can still be '
    'recognised after rotation.';

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_id ON auth.refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_family_id ON auth.refresh_tokens (family_id);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires_on ON auth.refresh_tokens (expires_on)
    WHERE revoked_on IS NULL;
