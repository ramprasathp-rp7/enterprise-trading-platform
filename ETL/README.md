# trading-analytics-etl

Incremental load from the operational Postgres schema into the DuckDB star
schema defined in `Database/duckdb/analytics/analytics-schema.sql` (binding — not modified
by this package).

## Setup

```bash
cd ETL
python3 -m venv .venv
.venv/bin/pip install -e '.[dev]'
.venv/bin/python -m pytest
```

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `PGHOST`, `PGPORT`, `POSTGRES_DB` (or `PGDATABASE`), `PGUSER`, `PGPASSWORD` | `localhost`, `5432`, `trading_system_db`, `postgres`, *(empty)* | Postgres connection |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | *(fallback only)* | Backwards-compatible aliases if PG* names are absent |
| `ANALYTICS_DUCKDB_PATH` | `Database/duckdb/analytics/analytics_store.duckdb` | Where the DuckDB file lives |
| `ANALYTICS_SCHEMA_PATH` | `Database/duckdb/analytics/analytics-schema.sql` | Path to the binding contract DDL |
| `ANALYTICS_STAGING_DIR` | `.staging` | Where `etl-extract` writes its JSON dumps |

## The three pipeline commands

```bash
etl-extract [out_dir]     # Postgres -> JSON files. Read-only, no DuckDB, no watermark advance.
etl-load                  # Full incremental load: extract + transform + quality checks + load.
                           # Advances the Postgres watermark on success.
etl-reconcile YYYY-MM-DD  # Compares that day's fact_trades against Postgres.
```

`etl-extract` is a standalone, side-effect-free way to see what a load
*would* pick up (dimensions in full, orders since the current watermark).
`etl-load` does not read `etl-extract`'s output — it performs its own
extraction internally, because validating a fact row needs the dimension
key maps as they exist *after* this run's `dim_instrument`/`dim_account`
load, not from a separately-staged snapshot. See `cli.py`'s module
docstring for the full reasoning.

## Load order (fixed, per the contract)

`dim_date` (once, full range) → `dim_instrument` (Type 1 upsert) →
`dim_account` (Type 2: close old version, insert new, skip unchanged) →
`fact_trades` (watermarked on `orders.created_on`, quality-checked, merged
on `source_order_id`).

## Watermark

Kept in the existing Postgres `analytics_watermarks` table (migration 009)
rather than moved into DuckDB — see `watermark.py`'s module docstring for
why. Advanced only after the DuckDB transaction for the batch has
committed, so a crash in between just causes a harmless idempotent re-read
next run.

Uses `created_on >=` against the watermark, not `>`. Because the fact merge
is idempotent (`ON CONFLICT (source_order_id) DO UPDATE`), re-reading the
boundary row on a tie is harmless — while `>` risks silently missing a
same-timestamp order that committed to Postgres after the watermark was
read (Postgres's `now()` is transaction-start time, not commit time, so two
concurrent inserts can share a `created_on`).

**Empty-store guard:** if `fact_trades` is empty but the watermark is
non-null (e.g. the DuckDB file was deleted/rebuilt without resetting the
watermark), the watermark is ignored and a full load runs instead of
silently skipping everything before the stale watermark.

## Quarantine (dead-lettered rows)

`dead_letter_trades` — created by this package, *not* part of
`contracts/analytics-schema.sql`, since that file is fixed. Lives in
DuckDB, in the same transaction as the fact/dimension writes, so a given
order is always in exactly one place: `fact_trades` or
`dead_letter_trades`, never neither. Keyed on `source_order_id` (upsert,
`attempts` increments on repeat failures — not one row per attempt). Rows
that later load successfully are removed from quarantine after each run, so
it only ever reflects what's currently unresolved. The full original order
is stored as JSON in `payload`, since it's the only record of the row once
the watermark advances past it.

## Reconciliation

`etl-reconcile` implements the contract's required check: for a given day,
row count and summed `trade_value` in `fact_trades` must equal the same
figures recomputed directly from Postgres `orders`. Also reports that day's
dead-letter count, so a mismatch fully explained by quarantined rows is
distinguishable from a real bug in the transform or merge logic.

## Repository layout note

This package lives at `ETL/` in the repository root and is run from there.
Any setup note that assumes a different folder name should be adapted to this
layout.
