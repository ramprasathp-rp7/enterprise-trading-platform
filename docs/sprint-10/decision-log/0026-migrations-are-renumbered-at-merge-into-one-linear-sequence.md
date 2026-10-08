# 0016 Migration files are renumbered at merge so the order-service sequence stays unique and linear

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | SarojaPandiyan |

## Context

`Database/postgres/order-service/apply.sh` runs every file in `migrations/` in filename order with
`psql -v ON_ERROR_STOP=1`. There is no tool that records which migrations have run, such as
Flyway or Liquibase. A database is built from scratch: all migrations, then the seed.

In Sprint 10 several pairs worked on branches at the same time, and each added the next free number
from its own branch. Merging produced collisions and out-of-order dependencies. For example,
`014_create_price_alerts.sql` and `014_create_candle_data_table.sql` were added alongside the
preferences pair's `014_create_customer_preferences.sql`. `017_add_price_alert_trigger_channels`
also has to run after the price alerts table exists.

## Options considered

| Option | For | Against |
|---|---|---|
| Leave the duplicate numbers alone, since filename order still sorts them | No renames | The order depends on the rest of the name. The README, the security review and other entries cite migrations by number, and "014" would mean three different files |
| Adopt Flyway now | Recorded history, checksums, and an error on duplicate versions | A new dependency and a baseline for every existing database, introduced in the last days of a sprint |
| Renumber the files at merge so each number is unique and dependencies come first, and update the documents that cite them | No new tooling. The sequence reads as the order things were merged | A rename is safe only because nothing records applied migrations. Every environment has to be rebuilt rather than patched |

## Decision

Renumber at merge (commits `14c708b` and `348001e`). The result is 016 price alerts, 017 price
alert trigger channels, 018 candle data, 019 candle purge, 020 candle watermarks and 021 order
notifications. The security review's references were updated in the same commit. What decided it
is that `apply.sh` already rebuilds from scratch, so a rename costs nothing at run time. Flyway's
benefit, safely patching a live database, isn't something this project does yet.

## Consequences

- Each migration number now names exactly one file, and a file's dependencies have lower numbers.
- **An existing local database must be dropped and rebuilt with `apply.sh`** after pulling a
  renumbering. Re-running against a database that is already populated fails at the first
  `CREATE TABLE` that already exists.
- The next collision will happen the same way. Agree a number before writing a migration, or
  renumber as part of the merge. If any environment ever has to be upgraded in place instead of
  rebuilt, this decision has to be replaced by a migration tool, and renaming files after that
  point is forbidden.
