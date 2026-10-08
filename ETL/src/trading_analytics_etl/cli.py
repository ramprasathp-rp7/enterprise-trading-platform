"""CLI entry points. See pyproject.toml [project.scripts] and the sprint
README for how these three map to "three pipeline commands a teammate can
run on its own":

  etl-extract    Connects to Postgres only. Prints/dumps what would be
                  extracted (dims in full, orders since the current
                  watermark) to JSON files under --out. Touches no DuckDB
                  state and advances no watermark — useful on its own to
                  verify what a load *would* pick up before running it.
  etl-load        The real pipeline: extract + transform + quality checks +
                  load into DuckDB, in the required dim_date -> dim_instrument
                  -> dim_account -> fact_trades order, then advances the
                  Postgres watermark. This is what you run to actually load.
  etl-reconcile   Compares a given day's fact_trades against Postgres.

Note on design: etl-load performs its own extraction internally rather than
reading etl-extract's output, because fact-row validation needs live
dimension key maps that only exist once dim_instrument/dim_account have
already been loaded in *this* run — there's no useful way to stage that
extract independently of the load it feeds. etl-extract is kept as a
read-only, side-effect-free way to inspect the same extract logic on its
own.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

import duckdb
import psycopg
from dotenv import load_dotenv

from . import extract, watermark
from .config import Config, _find_repo_root
from .pipeline import run_load
from .reconcile import reconcile_day


def _json_default(o):
    if isinstance(o, (datetime, date)):
        return o.isoformat()
    if isinstance(o, Decimal):
        return str(o)
    return str(o)


def extract_main() -> None:
    load_dotenv(_find_repo_root() / ".env")
    cfg = Config.from_env()
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else cfg.staging_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    with psycopg.connect(cfg.pg_dsn) as pg_conn:
        instruments = extract.extract_instruments(pg_conn)
        accounts = extract.extract_accounts(pg_conn)
        wm = watermark.get_watermark(pg_conn)
        orders = extract.extract_orders_since(pg_conn, wm)

    (out_dir / "instruments.json").write_text(json.dumps(instruments, default=_json_default, indent=2))
    (out_dir / "accounts.json").write_text(json.dumps(accounts, default=_json_default, indent=2))
    (out_dir / "orders.json").write_text(json.dumps(orders, default=_json_default, indent=2))

    print(
        f"extracted: {len(instruments)} instruments, {len(accounts)} accounts, "
        f"{len(orders)} orders (since watermark={wm}) -> {out_dir}"
    )


def load_main() -> None:
    load_dotenv(_find_repo_root() / ".env")
    cfg = Config.from_env()
    with psycopg.connect(cfg.pg_dsn) as pg_conn, duckdb.connect(cfg.duckdb_path) as duck_con:
        result = run_load(pg_conn, duck_con, cfg.schema_sql_path)

    print(f"batch_id:           {result.batch_id}")
    print(f"dim_instrument:     {result.dim_instrument_loaded}")
    print(f"dim_account:        {result.dim_account_loaded}")
    print(f"fact_trades loaded: {result.fact_trades_loaded}")
    print(f"dead-lettered:      {result.dead_lettered}")


def reconcile_main() -> None:
    load_dotenv(_find_repo_root() / ".env")
    parser = argparse.ArgumentParser(prog="etl-reconcile")
    parser.add_argument("date", help="Date to reconcile, YYYY-MM-DD")
    args = parser.parse_args()
    trade_date = date.fromisoformat(args.date)

    cfg = Config.from_env()
    with psycopg.connect(cfg.pg_dsn) as pg_conn, duckdb.connect(cfg.duckdb_path) as duck_con:
        result = reconcile_day(pg_conn, duck_con, trade_date)

    print(f"reconciliation for {trade_date}:")
    print(f"  postgres: count={result.postgres_count} total={result.postgres_total}"
          f" total_account={result.postgres_total_account}")
    print(f"  duckdb:   count={result.duckdb_count} total={result.duckdb_total}"
          f" total_account={result.duckdb_total_account}")
    print(f"  dead-lettered that day: {result.dead_lettered_count}")
    print(f"  OK: {result.ok}")
    if not result.ok:
        sys.exit(1)
