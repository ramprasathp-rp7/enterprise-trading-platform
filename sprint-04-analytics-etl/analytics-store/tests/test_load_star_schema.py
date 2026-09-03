import os
import sys
from pathlib import Path

import duckdb
import psycopg2
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "etl"))
import load_star_schema as etl  # noqa: E402


def _pg_dsn() -> str:
    return (
        f"host={os.environ.get('PGHOST', 'localhost')} "
        f"port={os.environ.get('PGPORT', '5432')} "
        f"dbname={os.environ.get('POSTGRES_DB', 'enterprise_trading_platform')} "
        f"user={os.environ.get('PGUSER', 'postgres')} "
        f"password={os.environ.get('PGPASSWORD', '')}"
    )


@pytest.fixture
def pg_conn():
    try:
        conn = psycopg2.connect(_pg_dsn())
    except psycopg2.OperationalError as exc:
        pytest.skip(f"no reachable Postgres at {_pg_dsn()!r} — apply Sprint 3's schema+seed first ({exc})")
    with conn.cursor() as cur:
        cur.execute("SELECT count(*) FROM orders")
        if cur.fetchone()[0] == 0:
            pytest.skip("orders table is empty — run Sprint 3's apply.sh (schema + seed) first")
    yield conn
    conn.close()


@pytest.fixture
def duck_path(tmp_path):
    return str(tmp_path / "analytics_store.duckdb")


def test_schema_applies_unmodified(duck_path):
    con = duckdb.connect(duck_path)
    etl.ensure_schema(con)
    tables = {r[0] for r in con.execute(
        "SELECT table_name FROM information_schema.tables WHERE table_schema='main'"
    ).fetchall()}
    con.close()
    assert tables == {"dim_account", "dim_instrument", "dim_date", "fact_trades"}


def test_load_order_dimensions_before_facts(pg_conn, duck_path):
    con = duckdb.connect(duck_path)
    etl.ensure_schema(con)

    # Deliberately try to load facts BEFORE dimensions: must fail loudly, not insert a fact row referencing a key that doesn't exist.
    with pytest.raises(ValueError, match="dimensions must be loaded before facts"):
        etl.load_fact_trades(pg_conn, con, instrument_keys={}, account_keys={})
    con.close()


def test_full_load_matches_postgres_row_counts(pg_conn, duck_path):
    con = duckdb.connect(duck_path)
    etl.ensure_schema(con)
    etl.load_dim_date(con)
    instrument_keys = etl.load_dim_instrument(pg_conn, con)
    account_keys = etl.load_dim_account(pg_conn, con)
    n_facts = etl.load_fact_trades(pg_conn, con, instrument_keys, account_keys)

    with pg_conn.cursor() as cur:
        cur.execute("SELECT count(*) FROM instruments")
        pg_instruments = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM accounts")
        pg_accounts = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM orders")
        pg_orders = cur.fetchone()[0]

    assert len(instrument_keys) == pg_instruments
    assert len(account_keys) == pg_accounts
    assert n_facts == pg_orders
    assert con.execute("SELECT count(*) FROM dim_instrument").fetchone()[0] == pg_instruments
    assert con.execute("SELECT count(*) FROM dim_account").fetchone()[0] == pg_accounts
    assert con.execute("SELECT count(*) FROM fact_trades").fetchone()[0] == pg_orders
    con.close()


def test_reload_is_idempotent(pg_conn, duck_path):
    con = duckdb.connect(duck_path)
    for _ in range(2):
        etl.ensure_schema(con)
        etl.load_dim_date(con)
        instrument_keys = etl.load_dim_instrument(pg_conn, con)
        account_keys = etl.load_dim_account(pg_conn, con)
        etl.load_fact_trades(pg_conn, con, instrument_keys, account_keys)

    counts = {
        t: con.execute(f"SELECT count(*) FROM {t}").fetchone()[0]
        for t in ("dim_date", "dim_instrument", "dim_account", "fact_trades")
    }
    con.close()

    with pg_conn.cursor() as cur:
        cur.execute("SELECT count(*) FROM instruments")
        pg_instruments = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM accounts")
        pg_accounts = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM orders")
        pg_orders = cur.fetchone()[0]

    # Ran twice; row counts must match Postgres exactly, not double.
    assert counts["dim_instrument"] == pg_instruments
    assert counts["dim_account"] == pg_accounts
    assert counts["fact_trades"] == pg_orders


def test_reconciliation_row_count_and_trade_value_match_postgres(pg_conn, duck_path):
    con = duckdb.connect(duck_path)
    etl.ensure_schema(con)
    etl.load_dim_date(con)
    instrument_keys = etl.load_dim_instrument(pg_conn, con)
    account_keys = etl.load_dim_account(pg_conn, con)
    etl.load_fact_trades(pg_conn, con, instrument_keys, account_keys)

    with pg_conn.cursor() as cur:
        cur.execute(
            """
            SELECT o.created_on::date, count(*), sum(o.quantity * COALESCE(o.executed_price, o.price))
            FROM orders o GROUP BY o.created_on::date
            """
        )
        pg_by_day = {r[0]: (r[1], r[2]) for r in cur.fetchall()}

    duck_by_day = {
        r[0]: (r[1], r[2])
        for r in con.execute(
            """
            SELECT d.full_date, count(*), sum(f.trade_value)
            FROM fact_trades f JOIN dim_date d ON d.date_key = f.date_key
            GROUP BY d.full_date
            """
        ).fetchall()
    }
    con.close()

    assert pg_by_day == duck_by_day


def test_account_status_change_creates_new_version_without_losing_history(pg_conn, duck_path):
    con = duckdb.connect(duck_path)
    etl.ensure_schema(con)
    etl.load_dim_date(con)
    instrument_keys = etl.load_dim_instrument(pg_conn, con)
    account_keys = etl.load_dim_account(pg_conn, con)
    etl.load_fact_trades(pg_conn, con, instrument_keys, account_keys)

    with pg_conn.cursor() as cur:
        cur.execute("SELECT account_id, status FROM accounts LIMIT 1")
        account_id, original_status = cur.fetchone()
        new_status = "SUSPENDED" if original_status != "SUSPENDED" else "ACTIVE"
        cur.execute("UPDATE accounts SET status = %s WHERE account_id = %s", [new_status, account_id])
        pg_conn.commit()

    try:
        instrument_keys = etl.load_dim_instrument(pg_conn, con)
        account_keys = etl.load_dim_account(pg_conn, con)
        etl.load_fact_trades(pg_conn, con, instrument_keys, account_keys)

        versions = con.execute(
            "SELECT status, is_current, end_date FROM dim_account WHERE account_id = ? ORDER BY account_key",
            [account_id],
        ).fetchall()
        assert len(versions) == 2
        assert versions[0] == (original_status, False, versions[0][2]) and versions[0][2] is not None
        assert versions[1] == (new_status, True, None)

        # Historical facts for this account must still point at the OLD (closed) version — the entire reason dim_account is Type 2.
        old_key = con.execute(
            "SELECT account_key FROM dim_account WHERE account_id = ? AND is_current = FALSE", [account_id]
        ).fetchone()[0]
        historical_fact_statuses = con.execute(
            "SELECT DISTINCT a.status FROM fact_trades f JOIN dim_account a ON a.account_key = f.account_key "
            "WHERE f.account_key = ?",
            [old_key],
        ).fetchall()
        for (status,) in historical_fact_statuses:
            assert status == original_status
    finally:
        with pg_conn.cursor() as cur:
            cur.execute("UPDATE accounts SET status = %s WHERE account_id = %s", [original_status, account_id])
            pg_conn.commit()
        con.close()
