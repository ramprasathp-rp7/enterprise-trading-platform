# The analytical star schema

Source of truth: `contracts/analytics-schema.sql`, applied to DuckDB **unmodified**
— verified directly: the file loads into DuckDB with zero syntax changes, which is
the portability the file's own header claims (plain ANSI SQL, no DuckDB dialect).

## Why a second schema exists

The Sprint 3 operational schema is normalised: a write touches one row, a
constraint can be trusted, an account's current balance is one lookup. That
shape is wrong for analysis — "trade value by asset class by quarter" against
normalised tables is several joins and a full scan, run while somebody is
trying to place an order on the same tables.

This schema answers the analytical questions instead: denormalised,
append-mostly, loaded by a separate ETL, never written to or read by a live
service. It's a star — one fact table surrounded by dimensions, joined on
integer surrogate keys — chosen over a snowflake specifically because join
depth stays at one, which is what keeps the queries readable by someone who
isn't an engineer.

## The four tables and their grain

Stating the grain (what one row *means*) before anything else, because
picking a grain and never writing it down is where most dimensional models
go wrong.

| Table | Grain — one row is... | Type |
|---|---|---|
| `dim_date` | one calendar day | Static. Populated once, ahead of everything else. |
| `dim_instrument` | one tradable instrument | Type 1 (overwrite in place) |
| `dim_account` | one **version** of one account | Type 2 (slowly changing) |
| `fact_trades` | one order, in whatever status it reached | Fact, at order grain |

### `dim_date` — one row per calendar day

Pre-populated for a wide range (2016-01-01 to 2035-12-31 in this ETL — covers
both the earliest Fauxnance data availability and a decade-plus of headroom)
*before* any fact is loaded, because a fact row can't resolve a `date_key`
that doesn't exist yet. Carries `day_of_week`, `day_name`, `month_name`, and
`is_weekday` as stored columns rather than computed per-query, so every
target (DuckDB, Snowflake, SQLite) renders identical labels and a chart
doesn't need vendor-specific date functions.

### `dim_instrument` — one row per instrument, Type 1

Merged on `symbol` (the natural key). A company changing its name doesn't
change the meaning of a trade from two years ago, so old attribute values
aren't worth keeping — the row is simply overwritten. `exchange` is derived
by the ETL from the Fauxnance symbol scheme (`.NS` → NSE, `.BO` → BSE, `FX:`
prefix → FX, `X:` prefix → CRYPTO, plain ticker → US) — the same scheme
Sprint 4's own pipeline already parses, which is what "volume by venue" (a
question the operational schema alone can't answer) rests on.

### `dim_account` — one row per account *version*, Type 2

Merged on `account_id` (the natural key), but unlike `dim_instrument`, a
status change doesn't overwrite the row — it closes the current version
(`end_date` set, `is_current = FALSE`) and inserts a new one. This is a Type
2 slowly-changing dimension specifically because account status changes the
*meaning* of a historical trade: a trade placed last month while the account
was `ACTIVE` must keep reporting as placed on an `ACTIVE` account even after
that account is later `SUSPENDED`. Overwriting the row instead of versioning
it would silently rewrite that history.

Verified directly against a real status change: `ACC-000001` moved from
`ACTIVE` to `SUSPENDED` in Postgres, the ETL was re-run, and the result was
two `dim_account` rows for that `account_id` — the old one closed
(`is_current = FALSE`, `end_date` set), a new one opened — while every
`fact_trades` row for that account's *earlier* orders kept pointing at the
old (now-closed) version, still showing `ACTIVE`. That's the property this
design exists to guarantee, and it held.

### `fact_trades` — one row per order

Grain is **one order**, not one execution and not one order-state change —
stated explicitly because grain ambiguity is the most common dimensional
modelling mistake. `REJECTED` and `CANCELLED` orders are loaded, not
filtered out: fill rate (`FILLED` ÷ total) is one of the five analytics
questions this model has to answer, and it's not computable from fills
alone.

`trade_value` is precomputed at load time — `quantity × executed_price`
where the order filled, `quantity × price` otherwise — rather than
recomputed in every downstream query, because it's the measure nearly every
question here aggregates. `quantity` sums meaningfully across rows; `price`
does not (averaging or summing a price across unrelated orders is
meaningless) — `trade_value` is what's built to be summed instead.

`source_order_id` (the operational `orders.id`, as text) carries a `UNIQUE`
constraint, which is what makes a re-run of this ETL idempotent: an order
already loaded is skipped, not duplicated, no matter how many times the
loader runs against it.

## Load order

Enforced in code, not just documented: `dim_date` → `dim_instrument` →
`dim_account` → `fact_trades`. A fact row resolves its dimension keys by
lookup at load time; if a dimension were loaded after its facts, the lookup
would fail for a reason that reads like a bug in the fact load rather than
what it actually is — a dimension that isn't there yet.

## Idempotency, verified

Every load function was re-run against unchanged source data with zero
side effects: `dim_date` inserts nothing once populated, `dim_instrument`
updates in place without growing, `dim_account` opens no new version when
status hasn't changed, and `fact_trades` inserts nothing for an
already-loaded `source_order_id`. Re-running this ETL after a fresh pull
from Postgres is always safe.

## Reconciliation

Per the schema's own instruction — *"the row count and summed trade_value in
fact_trades for a given day must equal the same figures computed against
Postgres for that day"* — this was run for real: grouping both `orders` (in
Postgres) and `fact_trades` (in DuckDB, joined through `dim_date`) by day,
comparing row count and summed value. Every one of the 13 distinct order
dates in the seed data matched exactly between the two stores.

## The five analytics questions this model has to answer

Each is one join per dimension, no subquery — if any of these were awkward
against this model, the model would be wrong, not the question.

| Question | Query shape |
|---|---|
| Trade volume | `COUNT(*)`, `SUM(trade_value)` by `date_key` |
| Most active accounts | `COUNT(*)` by `account_key`, joined to `dim_account WHERE is_current = TRUE` |
| Fill rate | `COUNT(status='FILLED') / COUNT(*)` by `date_key` — why rejected/cancelled orders are loaded at all |
| Exposure by instrument | `SUM(trade_value)` by `instrument_key` and `side`, joined to `dim_instrument` |
| Average trade size | `AVG(trade_value)` over a date range — note this averages *values*, never prices |

Example — fill rate by month:

```sql
SELECT d.year, d.month,
       COUNT(*) AS orders_placed,
       SUM(CASE WHEN f.status = 'FILLED' THEN 1 ELSE 0 END) AS orders_filled,
       SUM(CASE WHEN f.status = 'FILLED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS fill_rate_pct
  FROM fact_trades f
  JOIN dim_date d ON d.date_key = f.date_key
 GROUP BY d.year, d.month
 ORDER BY d.year, d.month;
```

One join, one `GROUP BY` — readable by a non-engineer, which is the actual
test of whether this is a correctly-shaped star.
