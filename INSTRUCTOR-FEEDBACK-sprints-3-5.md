# Team 1 — observations on `main`

_7 September 2026 · read against `main` at 95ea7e2_

## Where you stand

`main` is complete through Sprint 5 and the documentation is in good shape: EXPLAIN plans captured, ERD source in Mermaid, a written denormalisation rationale, enums and exceptions on the class diagram. Two things need attention before Sprint 6 builds on this. `56056f2` moved every Sprint 3 file into `sprint-03-trade-database/` without moving the harness with it, so `scripts/check.sh` and `APPLY_COMMAND` both point at paths that no longer exist. And `placeOrder` returns an order that is already `FILLED` at the customer's limit price — Sprint 6 absorbs that package as source this week.

## Sprint 3 — trade database

- **`scripts/check.sh` does not run.** `check.sh:7` sets `ROOT_DIR` to `sprint-03-trade-database/`, then `:25–26` requires `$ROOT_DIR/manifest.env` — which lives at the repository root. It exits at line 26 before touching a database. `manifest.env:2` says `APPLY_COMMAND="./apply.sh"`, and there is no `apply.sh` at the root. Two one-line fixes; until they are made you have no working harness.
- The probes cannot assert what they claim. `check.sh:70` runs `psql` without `--no-psqlrc` and without verbose verbosity, and `:74` greps the output for `23505` / `23503` — strings psql does not print at the default setting. The constraints themselves are correct; the harness cannot see them refuse a row.
- `design/indexes_importance.md` captures real `EXPLAIN ANALYZE` output for all three indexes and for `positions_pkey` and `uq_accounts_account_id` — the two queries already served by an existing key, correctly recorded as such. But every `orders` plan reads `rows=0.00`: taken against an empty table, so nothing shows about the index at scale, and the Q4 plan is honestly a `Seq Scan`. **Per-index write cost is absent.** You also have two index documents (`indexes.md`, `indexes_importance.md`) that disagree in scope.
- **The incremental-extract design contradicts itself.** `DESIGN.md:12` correctly names `updated_on` as the execution timestamp; `DESIGN.md:61` filters `created_on >= :last_extract_timestamp`. Orders mutate — `NEW` becomes `FILLED` days later — so a `created_on` watermark never re-reads a status change and freezes the analytical store. There is no index on `updated_on` (`001_create_trade_schema.sql:80–85`). `positions` has no `version` column while Sprint 7 puts two writers on it.
- `apply.sh` is the best script in the repository: `SCRIPT_DIR` resolution so it runs from anywhere, `ON_ERROR_STOP=1` with a comment explaining why. `design/ERD.mmd` uses `||--o{`, so optionality is expressed rather than implied.

## Sprint 4 — analytics and ETL

- `charts/daily-volume.html` is **8.2 MB against 4.1 MB for both siblings**, and the embedded plotly string appears twice in it where it appears once in the others. That is the shape of a corrected document placed in front of a broken one rather than replacing it. Regenerate it with `python -m trading_analytics.dashboard` and commit the output untouched.
- Chart titles and legends name variables rather than findings: `dashboard.py:37, 50, 64` set titles like a column header, and `:35, 47, 62` pass `name=symbol` with no company name. The reader has your chart and no access to you.
- `analytics-store/etl/load_star_schema.py:205–210` full-scans `orders` with no watermark clause. `psycopg2` is undeclared in `pyproject.toml`.
- The root `README.md` describes `contracts/` and `infra/` directories that do not exist on `main`, and tells a reader to `cp .env.example .env` when the committed file is `.example.env`.
- `claims.md` is honest — a withdrawn fourth claim and a caveat on claim 3. Keep that habit.

## Sprint 5 — domain engine

- `OrderPlacementService.java:95` calls `order.markFilled(price)` after the debit and the position move, so `placeOrder` returns `FILLED` at the customer's own limit price. Seven assertions in `OrderLogicTest` bake it in. Sprint 6 wraps this method in a controller and Sprint 7's Trade Executor has to unpick it, because the executed price does not exist until the executor achieves it against a live quote.
- The comment at `:34–37` defends the inline rule sequence as contractual ordering. It is — but the method also carries three responsibilities: validation, settlement and state transition. Separating the second from the first is what makes Sprint 7 possible without rewriting the rules.
- A refused order never exists: the `Order` is constructed at `:84`, after all eight rules pass. The order table is the audit trail, and a rejected order belongs in it.
- `IdempotencyKeyChecker` is `boolean isAlreadyUsed(String)`. Behind MyBatis in Sprint 6, two concurrent requests carrying the same key both read `false`. The authority has to be the `UNIQUE` constraint, and the seam has to let the insert be the check.
- `classDiagram.mmd` matches the code type for type — all four enums, `DomainException` and its seven subtypes, `ErrorCode`, `PlaceOrderRequest`, `IdempotencyKeyChecker`. Keep it that way through the Sprint 6 refactor.

## Do these before the Sprint 6 checkpoint

1. **Un-settle `placeOrder`.** Return the order `NEW`; move `acc.debit` / `credit`, the position update and `markFilled` into a separate `settle(Order, BigDecimal)`. Do it before the Sprint 6 controller wraps the current shape.
2. **Repair the harness.** Set `manifest.env:2` to `APPLY_COMMAND="sprint-03-trade-database/apply.sh"`, and fix `check.sh:7` to resolve the repo root two levels up (with `probes/` paths adjusted). Then run it end to end on a machine that is not yours.
3. **Make the probes assert what they claim.** Add `--no-psqlrc -v VERBOSITY=verbose` to the `psql` call at `check.sh:70` so the SQLSTATE the grep on `:74` looks for is actually printed.
4. **Fix the watermark.** Change `DESIGN.md:61` to window on `updated_on`, add `CREATE INDEX ix_orders_updated_on ON orders (updated_on)` in a new `migrations/002_*.sql`, and put the same `WHERE` on the query at `load_star_schema.py:205`.
5. **Reshape rule 8** to `void claim(String idempotencyKey) throws DuplicateOrderException`, called at insert time, so the Sprint 6 implementation can turn a `23505` into the refusal.
6. **Retitle the charts.** Put each claim's finding in the `title` at `dashboard.py:37, 50, 64`, add company names alongside the tickers in `name=`, regenerate all three, and commit them unedited.
7. Add `version INTEGER NOT NULL DEFAULT 0` to `positions` in that same `002_` migration, and re-capture the index plans against the seeded database with one line of write cost per index in `indexes_importance.md` (then delete `indexes.md`).

## Keep doing

- `apply.sh`. It survived the restructure and it is the model for every script that follows.
- The exception hierarchy with catalogue codes and typed investigation fields, and `PlaceOrderRequestValidationTest`'s both-sides-of-every-boundary coverage. Sprint 6 must not flatten either into HTTP statuses and happy-path tests.
- `claims.md`'s honesty. Carry it into the Sprint 6 API documentation.
