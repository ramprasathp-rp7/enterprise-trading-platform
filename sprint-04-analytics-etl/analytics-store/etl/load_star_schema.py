from __future__ import annotations

import argparse
import os
import sys
from datetime import date, timedelta

import duckdb
import psycopg2
import psycopg2.extras
from dotenv import load_dotenv

load_dotenv()

SCHEMA_SQL_PATH = os.path.join(os.path.dirname(__file__), "..", "schema", "analytics-schema.sql")

DIM_DATE_START = date(2016, 1, 1)
DIM_DATE_END = date(2035, 12, 31)

WEEKDAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
MONTH_NAMES = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]


def get_pg_connection(dsn: str):
    return psycopg2.connect(dsn)


def ensure_schema(duck_con: duckdb.DuckDBPyConnection) -> None:
    existing = {
        r[0]
        for r in duck_con.execute(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'main'"
        ).fetchall()
    }
    if {"dim_account", "dim_instrument", "dim_date", "fact_trades"}.issubset(existing):
        return
    with open(SCHEMA_SQL_PATH, "r", encoding="utf-8") as f:
        duck_con.execute(f.read())


def load_dim_date(duck_con: duckdb.DuckDBPyConnection, start: date = DIM_DATE_START, end: date = DIM_DATE_END) -> int:
    existing = duck_con.execute("SELECT count(*) FROM dim_date").fetchone()[0]
    if existing > 0:
        return 0

    rows = []
    d = start
    while d <= end:
        date_key = int(d.strftime("%Y%m%d"))
        quarter = (d.month - 1) // 3 + 1
        rows.append(
            (
                date_key,
                d,
                d.day,
                d.month,
                d.year,
                quarter,
                d.weekday(),
                WEEKDAY_NAMES[d.weekday()],
                MONTH_NAMES[d.month - 1],
                d.weekday() < 5,
            )
        )
        d += timedelta(days=1)

    duck_con.executemany(
        """
        INSERT INTO dim_date
            (date_key, full_date, day, month, year, quarter, day_of_week, day_name, month_name, is_weekday)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    return len(rows)


def _derive_exchange(symbol: str) -> str:
    if symbol.endswith(".NS"):
        return "NSE"
    if symbol.endswith(".BO"):
        return "BSE"
    if symbol.startswith("FX:"):
        return "FX"
    if symbol.startswith("X:"):
        return "CRYPTO"
    return "US"


def load_dim_instrument(pg_con, duck_con: duckdb.DuckDBPyConnection) -> dict[str, int]:
    with pg_con.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute("SELECT symbol, name, asset_class, currency, tradable FROM instruments ORDER BY symbol")
        source_rows = cur.fetchall()

    existing = dict(duck_con.execute("SELECT symbol, instrument_key FROM dim_instrument").fetchall())
    next_key = (duck_con.execute("SELECT coalesce(max(instrument_key), 0) FROM dim_instrument").fetchone()[0]) + 1

    key_by_symbol: dict[str, int] = {}
    for row in source_rows:
        symbol = row["symbol"]
        exchange = _derive_exchange(symbol)
        if symbol in existing:
            instrument_key = existing[symbol]
            duck_con.execute(
                """
                UPDATE dim_instrument
                   SET name = ?, asset_class = ?, currency = ?, exchange = ?, tradable = ?, loaded_at = now()
                 WHERE instrument_key = ?
                """,
                [row["name"], row["asset_class"], row["currency"], exchange, row["tradable"], instrument_key],
            )
        else:
            instrument_key = next_key
            next_key += 1
            duck_con.execute(
                """
                INSERT INTO dim_instrument
                    (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                """,
                [instrument_key, symbol, row["name"], row["asset_class"], row["currency"], exchange, row["tradable"]],
            )
        key_by_symbol[symbol] = instrument_key

    return key_by_symbol


def load_dim_account(pg_con, duck_con: duckdb.DuckDBPyConnection) -> dict[str, int]:
    with pg_con.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            "SELECT account_id, holder_name, status, id AS source_id, created_on FROM accounts ORDER BY account_id"
        )
        source_rows = cur.fetchall()

    current = {
        r[0]: (r[1], r[2])
        for r in duck_con.execute(
            "SELECT account_id, account_key, status FROM dim_account WHERE is_current = TRUE"
        ).fetchall()
    }
    next_key = (duck_con.execute("SELECT coalesce(max(account_key), 0) FROM dim_account").fetchone()[0]) + 1

    today = date.today()
    key_by_account_id: dict[str, int] = {}
    for row in source_rows:
        account_id = row["account_id"]
        existing_current = current.get(account_id)

        if existing_current is None:
            # First time seeing this account: one version, effective from its Postgres creation date.
            account_key = next_key
            next_key += 1
            effective_date = row["created_on"].date()
            duck_con.execute(
                """
                INSERT INTO dim_account
                    (account_key, account_id, holder_name, status, effective_date,
                     end_date, is_current, source_id, loaded_at)
                VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, now())
                """,
                [account_key, account_id, row["holder_name"], row["status"], effective_date, row["source_id"]],
            )
            key_by_account_id[account_id] = account_key
            continue

        existing_key, existing_status = existing_current
        if existing_status == row["status"]:
            # No change: neither closes nor inserts anything.
            key_by_account_id[account_id] = existing_key
            continue

        # Status changed: close the current version, open a new one.
        duck_con.execute(
            "UPDATE dim_account SET end_date = ?, is_current = FALSE WHERE account_key = ?",
            [today - timedelta(days=1), existing_key],
        )
        account_key = next_key
        next_key += 1
        duck_con.execute(
            """
            INSERT INTO dim_account
                (account_key, account_id, holder_name, status, effective_date,
                 end_date, is_current, source_id, loaded_at)
            VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, now())
            """,
            [account_key, account_id, row["holder_name"], row["status"], today, row["source_id"]],
        )
        key_by_account_id[account_id] = account_key

    return key_by_account_id


def load_fact_trades(
    pg_con,
    duck_con: duckdb.DuckDBPyConnection,
    instrument_keys: dict[str, int],
    account_keys: dict[str, int],
) -> int:
    with pg_con.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT o.id AS order_id, a.account_id, i.symbol, o.side, o.quantity,
                   o.price, o.executed_price, o.status, o.created_on
              FROM orders o
              JOIN accounts a ON a.id = o.account_id
              JOIN instruments i ON i.id = o.instrument_id
             ORDER BY o.created_on
            """
        )
        source_rows = cur.fetchall()

    already_loaded = {
        r[0] for r in duck_con.execute("SELECT source_order_id FROM fact_trades").fetchall()
    }
    next_key = (duck_con.execute("SELECT coalesce(max(trade_key), 0) FROM fact_trades").fetchone()[0]) + 1

    inserted = 0
    for row in source_rows:
        source_order_id = str(row["order_id"])
        if source_order_id in already_loaded:
            continue

        account_key = account_keys.get(row["account_id"])
        instrument_key = instrument_keys.get(row["symbol"])
        if account_key is None or instrument_key is None:
            raise ValueError(
                f"order {source_order_id} resolves to a missing dimension key "
                f"(account={row['account_id']!r}, instrument={row['symbol']!r}) "
                "— dimensions must be loaded before facts"
            )

        created_on = row["created_on"]
        date_key = int(created_on.strftime("%Y%m%d"))

        price_for_value = row["executed_price"] if row["status"] == "FILLED" else row["price"]
        trade_value = row["quantity"] * price_for_value

        duck_con.execute(
            """
            INSERT INTO fact_trades
                (trade_key, account_key, instrument_key, date_key, side, quantity, price, status,
                 executed_price, trade_value, source_order_id, created_at, loaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """,
            [
                next_key,
                account_key,
                instrument_key,
                date_key,
                row["side"],
                row["quantity"],
                row["price"],
                row["status"],
                row["executed_price"],
                trade_value,
                source_order_id,
                created_on,
            ],
        )
        next_key += 1
        inserted += 1

    return inserted


def run(pg_dsn: str, duckdb_path: str) -> None:
    pg_con = get_pg_connection(pg_dsn)
    duck_con = duckdb.connect(duckdb_path)
    try:
        ensure_schema(duck_con)

        n_dates = load_dim_date(duck_con)
        print(f"dim_date: {n_dates} rows inserted ({'already populated' if n_dates == 0 else 'first load'})")

        instrument_keys = load_dim_instrument(pg_con, duck_con)
        print(f"dim_instrument: {len(instrument_keys)} instruments merged")

        account_keys = load_dim_account(pg_con, duck_con)
        print(f"dim_account: {len(account_keys)} accounts merged")

        n_facts = load_fact_trades(pg_con, duck_con, instrument_keys, account_keys)
        print(f"fact_trades: {n_facts} new orders loaded")
    finally:
        pg_con.close()
        duck_con.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Load the star schema from Postgres into DuckDB.")
    parser.add_argument(
        "--pg-dsn",
        default=os.environ.get(
            "POSTGRES_DSN",
            f"host={os.environ.get('PGHOST', 'localhost')} "
            f"port={os.environ.get('PGPORT', '5432')} "
            f"dbname={os.environ.get('POSTGRES_DB', 'enterprise_trading_platform')} "
            f"user={os.environ.get('PGUSER', 'postgres')} "
            f"password={os.environ.get('PGPASSWORD', '')}",
        ),
    )
    parser.add_argument("--duckdb-path", default="analytics_store.duckdb")
    args = parser.parse_args()
    run(args.pg_dsn, args.duckdb_path)