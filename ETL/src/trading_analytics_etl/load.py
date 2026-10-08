"""Load phase: everything that touches DuckDB.

contracts/analytics-schema.sql is applied unchanged (it's binding — see the
file's own header). dead_letter_trades is *not* part of that contract; it's
created here, by the pipeline, as documented in the sprint README's
extension notes. Keeping it in DuckDB (not Postgres) means one row from one
batch is always in exactly one place — fact_trades or dead_letter_trades —
because both writes happen inside the same DuckDB transaction.
"""
from __future__ import annotations

import json
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Optional

DEAD_LETTER_DDL = """
CREATE TABLE IF NOT EXISTS dead_letter_trades (
    source_order_id  VARCHAR(36)   NOT NULL,
    reason_code      VARCHAR(50)   NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    batch_id         VARCHAR(36)   NOT NULL,
    attempts         INTEGER       NOT NULL DEFAULT 1,
    first_seen_at    TIMESTAMP     NOT NULL,
    last_seen_at     TIMESTAMP     NOT NULL,
    payload          VARCHAR       NOT NULL,

    CONSTRAINT pk_dead_letter_trades PRIMARY KEY (source_order_id)
);
"""


def ensure_schema(con, schema_sql_path: str) -> None:
    """Apply the contract DDL if fact_trades doesn't exist yet, then ensure
    the (non-contract) dead_letter_trades table exists. Idempotent — safe to
    call at the start of every run."""
    exists = con.execute(
        "SELECT 1 FROM information_schema.tables WHERE table_name = 'fact_trades'"
    ).fetchone()
    if not exists:
        sql_text = Path(schema_sql_path).read_text()
        con.execute(sql_text)
    # Multi-currency columns (migration 022), for stores created before the schema had them
    con.execute("ALTER TABLE fact_trades ADD COLUMN IF NOT EXISTS fx_rate DECIMAL(20,10) DEFAULT 1")
    con.execute("ALTER TABLE fact_trades ADD COLUMN IF NOT EXISTS trade_value_account DECIMAL(18,2)")
    con.execute("UPDATE fact_trades SET trade_value_account = trade_value WHERE trade_value_account IS NULL")
    con.execute(DEAD_LETTER_DDL)


def is_dim_date_populated(con) -> bool:
    (count,) = con.execute("SELECT COUNT(*) FROM dim_date").fetchone()
    return count > 0


# --------------------------------------------------------------------------
# dim_date
# --------------------------------------------------------------------------

def load_dim_date(con, rows: list[dict]) -> int:
    if not rows:
        return 0
    con.executemany(
        """
        INSERT INTO dim_date
            (date_key, full_date, day, month, year, quarter,
             day_of_week, day_name, month_name, is_weekday)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT DO NOTHING
        """,
        [
            (
                r["date_key"], r["full_date"], r["day"], r["month"], r["year"],
                r["quarter"], r["day_of_week"], r["day_name"], r["month_name"], r["is_weekday"],
            )
            for r in rows
        ],
    )
    return len(rows)


def get_known_date_keys(con) -> set[int]:
    return {row[0] for row in con.execute("SELECT date_key FROM dim_date").fetchall()}


# --------------------------------------------------------------------------
# dim_instrument — Type 1 upsert
# --------------------------------------------------------------------------

def get_next_instrument_key(con) -> int:
    (next_key,) = con.execute(
        "SELECT COALESCE(MAX(instrument_key), 0) + 1 FROM dim_instrument"
    ).fetchone()
    return next_key


def load_dim_instrument(con, rows: list[dict], next_key_start: int) -> int:
    if not rows:
        return 0
    key = next_key_start
    params = []
    for r in rows:
        params.append(
            (key, r["symbol"], r["name"], r["asset_class"], r["currency"],
             r["exchange"], r["tradable"], r["loaded_at"])
        )
        key += 1
    con.executemany(
        """
        INSERT INTO dim_instrument
            (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (symbol) DO UPDATE SET
            name = EXCLUDED.name,
            asset_class = EXCLUDED.asset_class,
            currency = EXCLUDED.currency,
            exchange = EXCLUDED.exchange,
            tradable = EXCLUDED.tradable,
            loaded_at = EXCLUDED.loaded_at
        """,
        params,
    )
    return len(rows)


def get_instrument_key_map(con) -> dict[str, int]:
    return dict(con.execute("SELECT symbol, instrument_key FROM dim_instrument").fetchall())


# --------------------------------------------------------------------------
# dim_account — Type 2: close old, insert new
# --------------------------------------------------------------------------

def get_next_account_key(con) -> int:
    (next_key,) = con.execute(
        "SELECT COALESCE(MAX(account_key), 0) + 1 FROM dim_account"
    ).fetchone()
    return next_key


def get_current_accounts_by_account_id(con) -> dict[str, dict]:
    rows = con.execute(
        """
        SELECT account_id, holder_name, status, account_key
          FROM dim_account
         WHERE is_current = TRUE
        """
    ).fetchall()
    return {
        r[0]: {"holder_name": r[1], "status": r[2], "account_key": r[3]}
        for r in rows
    }


def get_account_key_map(con) -> dict[str, int]:
    return {
        account_id: attrs["account_key"]
        for account_id, attrs in get_current_accounts_by_account_id(con).items()
    }


def close_current_account_versions(con, account_ids: list[str], end_date: date) -> None:
    if not account_ids:
        return
    con.executemany(
        """
        UPDATE dim_account
           SET end_date = ?, is_current = FALSE
         WHERE account_id = ? AND is_current = TRUE
        """,
        [(end_date, account_id) for account_id in account_ids],
    )


def insert_new_account_versions(con, rows: list[dict], next_key_start: int) -> int:
    if not rows:
        return 0
    key = next_key_start
    params = []
    for r in rows:
        params.append(
            (key, r["account_id"], r["holder_name"], r["status"], r["effective_date"],
             r["end_date"], r["is_current"], r["source_id"], r["loaded_at"])
        )
        key += 1
    con.executemany(
        """
        INSERT INTO dim_account
            (account_key, account_id, holder_name, status, effective_date,
             end_date, is_current, source_id, loaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        params,
    )
    return len(rows)


# --------------------------------------------------------------------------
# fact_trades — idempotent merge on source_order_id
# --------------------------------------------------------------------------

def get_next_trade_key(con) -> int:
    (next_key,) = con.execute(
        "SELECT COALESCE(MAX(trade_key), 0) + 1 FROM fact_trades"
    ).fetchone()
    return next_key


def load_fact_trades(con, rows: list[dict]) -> int:
    if not rows:
        return 0
    con.executemany(
        """
        INSERT INTO fact_trades
            (trade_key, account_key, instrument_key, date_key, side, quantity, price, status,
             executed_price, trade_value, fx_rate, trade_value_account, source_order_id, created_at, loaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (source_order_id) DO UPDATE SET
            status = EXCLUDED.status,
            executed_price = EXCLUDED.executed_price,
            trade_value = EXCLUDED.trade_value,
            fx_rate = EXCLUDED.fx_rate,
            trade_value_account = EXCLUDED.trade_value_account,
            loaded_at = EXCLUDED.loaded_at
        """,
        [
            (
                r["trade_key"], r["account_key"], r["instrument_key"], r["date_key"],
                r["side"], r["quantity"], r["price"], r["status"], r["executed_price"],
                r["trade_value"], r.get("fx_rate", Decimal("1")),
                r.get("trade_value_account", r["trade_value"]),
                r["source_order_id"], r["created_at"], r["loaded_at"],
            )
            for r in rows
        ],
    )
    return len(rows)


# --------------------------------------------------------------------------
# dead_letter_trades — upsert on source_order_id, attempts increments
# --------------------------------------------------------------------------

def load_dead_letters(con, failures: list[dict], batch_id: str, seen_at: datetime) -> int:
    """failures: list of {source_order_id, reason_code, reason, payload (dict)}"""
    if not failures:
        return 0
    con.executemany(
        """
        INSERT INTO dead_letter_trades
            (source_order_id, reason_code, reason, batch_id, attempts, first_seen_at, last_seen_at, payload)
        VALUES (?, ?, ?, ?, 1, ?, ?, ?)
        ON CONFLICT (source_order_id) DO UPDATE SET
            reason_code  = EXCLUDED.reason_code,
            reason       = EXCLUDED.reason,
            batch_id     = EXCLUDED.batch_id,
            attempts     = dead_letter_trades.attempts + 1,
            last_seen_at = EXCLUDED.last_seen_at,
            payload      = EXCLUDED.payload
        """,
        [
            (
                f["source_order_id"], f["reason_code"], f["reason"], batch_id,
                seen_at, seen_at, json.dumps(f["payload"], default=_json_default),
            )
            for f in failures
        ],
    )
    return len(failures)


def cleanup_resolved_dead_letters(con) -> int:
    """Remove quarantine rows for orders that now successfully appear in
    fact_trades, so dead_letter_trades only ever reflects what's currently
    unresolved."""
    result = con.execute(
        """
        DELETE FROM dead_letter_trades
         WHERE source_order_id IN (SELECT source_order_id FROM fact_trades)
        """
    )
    return result.fetchall() and 0 or 0  # DuckDB DELETE doesn't return a count portably; caller can re-query if needed


def _json_default(o):
    if isinstance(o, (datetime, date)):
        return o.isoformat()
    if isinstance(o, Decimal):
        return str(o)
    return str(o)
