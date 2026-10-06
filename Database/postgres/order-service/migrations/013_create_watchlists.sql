-- Migration 013: Watchlists
--
-- A customer can keep any number of named watchlists, each holding a set of instrument symbols.
-- Both tables start empty; rows are created through the Watchlist API
-- (POST /api/v1/accounts/{accountId}/watchlists and .../watchlists/{watchlistId}/items).
--
-- Unlike orders and positions, watchlists are user-curated and carry no audit value,
-- so they are hard-deleted: deleting a watchlist cascades to its items.

CREATE TABLE watchlists (
    watchlist_id  BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(60)   NOT NULL,
    account_id    BIGINT        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_watchlists_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- One account cannot have two watchlists with the same name (case-insensitive).
CREATE UNIQUE INDEX uq_watchlists_account_name ON watchlists (account_id, lower(name));

COMMENT ON TABLE watchlists IS
    'Named lists of instruments a customer wants to follow. updated_at moves whenever '
    'the list is renamed or an item is added/removed.';

CREATE TABLE watchlist_items (
    watchlist_item_id  BIGSERIAL     PRIMARY KEY,
    watchlist_id       BIGINT        NOT NULL REFERENCES watchlists(watchlist_id) ON DELETE CASCADE,
    account_id         BIGINT        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol             VARCHAR(20)   NOT NULL REFERENCES instruments(symbol) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_watchlist_items_watchlist_symbol UNIQUE (watchlist_id, symbol)
);

COMMENT ON COLUMN watchlist_items.account_id IS
    'Denormalised owner of the parent watchlist, so ownership checks and per-account '
    'queries do not need a join.';

CREATE INDEX ix_watchlists_account ON watchlists (account_id, created_at);
CREATE INDEX ix_watchlist_items_account ON watchlist_items (account_id);

CREATE OR REPLACE FUNCTION set_watchlists_updated_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_watchlists_set_updated
    BEFORE UPDATE ON watchlists
    FOR EACH ROW
    EXECUTE FUNCTION set_watchlists_updated_timestamp();
