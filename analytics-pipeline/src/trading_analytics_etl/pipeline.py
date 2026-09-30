"""Orchestrates one full incremental load: dim_date -> dim_instrument ->
dim_account -> fact_trades, per contracts/analytics-schema.sql's load-order
section. This is what `etl-load` (see cli.py) calls.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import date, datetime, timezone

from . import extract, load, transform, watermark

DIM_DATE_START = date(2016, 1, 1)
DIM_DATE_END = date(2035, 12, 31)


@dataclass
class LoadResult:
    batch_id: str
    dim_instrument_loaded: int
    dim_account_loaded: int
    fact_trades_loaded: int
    dead_lettered: int


def run_load(pg_conn, duck_con, schema_sql_path: str) -> LoadResult:
    batch_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)

    load.ensure_schema(duck_con, schema_sql_path)

    # 1. dim_date — once, ahead of everything, full range.
    if not load.is_dim_date_populated(duck_con):
        load.load_dim_date(duck_con, transform.build_dim_date_rows(DIM_DATE_START, DIM_DATE_END))

    # 2. dim_instrument — full snapshot, Type 1 upsert.
    raw_instruments = extract.extract_instruments(pg_conn)
    instrument_rows = [transform.transform_instrument(r, now) for r in raw_instruments]
    next_instrument_key = load.get_next_instrument_key(duck_con)
    dim_instrument_loaded = load.load_dim_instrument(duck_con, instrument_rows, next_instrument_key)

    # 3. dim_account — full snapshot, Type 2 diff: close old, insert new,
    #    skip unchanged.
    raw_accounts = extract.extract_accounts(pg_conn)
    current_accounts = load.get_current_accounts_by_account_id(duck_con)
    diff = transform.diff_accounts(raw_accounts, current_accounts, now.date(), now)
    load.close_current_account_versions(duck_con, diff.to_close, now.date())
    next_account_key = load.get_next_account_key(duck_con)
    dim_account_loaded = load.insert_new_account_versions(duck_con, diff.to_insert, next_account_key)

    # Key maps + known dates, built from what's now in DuckDB — must happen
    # after steps 1-3, since fact validation resolves against these.
    instrument_key_map = load.get_instrument_key_map(duck_con)
    account_key_map = load.get_account_key_map(duck_con)
    known_date_keys = load.get_known_date_keys(duck_con)

    # 4. fact_trades — watermarked, quality-checked, merged.
    wm = watermark.get_watermark(pg_conn)

    # Empty-store guard: if fact_trades is empty but Postgres thinks we've
    # already watermarked past some point (e.g. the DuckDB file was deleted
    # and rebuilt without resetting the watermark), do a full load instead
    # of silently skipping everything before the stale watermark.
    (fact_count,) = duck_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()
    if fact_count == 0:
        wm = None

    raw_orders = extract.extract_orders_since(pg_conn, wm)

    valid_rows: list[dict] = []
    failures: list[dict] = []
    next_trade_key = load.get_next_trade_key(duck_con)

    for order in raw_orders:
        try:
            row = transform.validate_and_build_fact_row(
                order, instrument_key_map, account_key_map, known_date_keys,
                next_trade_key, now,
            )
            valid_rows.append(row)
            next_trade_key += 1
        except transform.QualityError as e:
            failures.append(
                {
                    "source_order_id": e.source_order_id,
                    "reason_code": e.reason_code,
                    "reason": e.message,
                    "payload": order,
                }
            )

    fact_trades_loaded = load.load_fact_trades(duck_con, valid_rows)
    dead_lettered = load.load_dead_letters(duck_con, failures, batch_id, now)
    load.cleanup_resolved_dead_letters(duck_con)

    # Everything above is one DuckDB connection/transaction; commit before
    # touching the Postgres watermark.
    duck_con.commit()

    if raw_orders:
        max_created_on = max(o["created_on"] for o in raw_orders)
        watermark.advance_watermark(pg_conn, max_created_on, uuid.UUID(batch_id))

    return LoadResult(
        batch_id=batch_id,
        dim_instrument_loaded=dim_instrument_loaded,
        dim_account_loaded=dim_account_loaded,
        fact_trades_loaded=fact_trades_loaded,
        dead_lettered=dead_lettered,
    )
