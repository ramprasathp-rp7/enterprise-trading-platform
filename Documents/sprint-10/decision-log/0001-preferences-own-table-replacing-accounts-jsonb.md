# 0001 Preferences live in a module-owned table, replacing the accounts.preferences JSONB column

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner) |

## Context

An earlier sprint left a preferences feature in the Trade REST API. It is a nullable `preferences
JSONB` column added to the shared `accounts` table by migration 006. A `PreferenceController`,
`PreferenceService` and `PreferenceMapper` in the layered `api.*` packages read and write it as an
untyped key/value map, merged with defaults from `application.properties`. It holds no alert
channel and no contact detail, and nothing calls it: the Angular app has no client for it and it
is not in `Contracts/trade-api.yaml`. The Sprint 10 brief requires each extension to be one package
with its own tables, and requires a resolution interface for notifications.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep the JSONB column and add `alertChannel` / `contactDetail` keys | No migration; reuses existing code | Writes to `accounts`, a table owned by the account layer and read by the analytics extract. An untyped map can't express "SMS needs a number". Encrypting one key inside a JSONB blob is awkward. The code sits in shared packages, so the boundary can't be shown |
| Move the existing classes into a package and keep the column | Less churn | Still writes to another module's table, which is the boundary violation the review looks for first |
| New `customer_preferences` table (migration 014) and a new `com.tradingplatform.preferences` module; delete the old code | Own table and own package, so the boundary is visible and testable (ArchUnit). Typed columns with DB constraints (channel enum, contact required unless IN_APP) | Old code is discarded. The 006 column has to be dropped by a follow-up migration (015) |

## Decision

Build a new module over its own `customer_preferences` table and delete the old implementation.
The deciding factor was that the brief marks reading or writing another module's table as the
primary boundary finding. The old design does that by construction, and nothing depended on it,
so removing it cost nothing in regressions.

## Consequences

- The old routes (`/api/v1/accounts/{id}/preferences` with a free-form map, and
  `/api/v1/preferences/defaults`) are gone. The path is reused by the new contract
  `Frontend/trading-ui/contracts/preferences-api.yaml` with a different body.
- `accounts.preferences` is dropped by migration 015 (`DROP COLUMN IF EXISTS`). We first checked
  that nothing reads it: both `AccountMapper`s (order-service and executor) and the analytics
  extract select named columns, the column was empty on every local account, and no view depends
  on it. Migration 006 is kept, marked superseded, so a fresh database replays in order. Anyone
  with an existing database applies 015 once.
- `ErrorCode.INVALID_PREFERENCE` (PREF-422) and `InvalidPreferenceException` were kept and reused,
  so the platform error catalogue did not grow.
- Account existence is checked through `AccountService`, never by querying `accounts` from the
  module. This is enforced by `PreferencesModuleBoundaryTest`.
