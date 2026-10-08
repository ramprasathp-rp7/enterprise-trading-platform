# 0014 An admin fee edit updates the asset row in place and applies to orders placed afterwards

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |
| Decided by | SarojaPandiyan (admin fee management) |

## Context

Fees are one row per asset class in `assets`: a `flat_fee` and a `percentage_fee` in decimal form
(0.0001 = 0.01%). `ck_assets_at_least_one` requires at least one of them to be above zero. Until
this change the admin screen could only show the fees, and changing them took SQL.

`FeeService` reads the row for each order when the order is placed. The fee is written onto the
order (`orders.fee`, migration 003) and is then used for the reservation and for settlement
(0008).

## Options considered

| Option | For | Against |
|---|---|---|
| Update the `assets` row in place (`PUT /api/v1/admin/fees/{assetType}`) | Smallest change. Fee lookup is unchanged. New orders get the new fee straight away | Keeps no history of what the fee used to be, or who changed it and when, apart from `updated_on` |
| Effective-dated fee rows (a new row per change, with `effective_from`) | Full history. Changes can be scheduled ahead | Every fee lookup becomes a date-range query. A schema change and a migration of the seed data. Nobody asked for scheduling |
| Keep fees in config and restart to change them | Nothing to build | Needs a deployment for a business change, which is exactly what the admin screen is meant to remove |

## Decision

Update in place. What decided it is that past orders already carry their own fee in
`orders.fee`. History of what each trade was charged therefore already exists where it matters,
and keeping the schedule's history adds nothing a statement or reconciliation needs.

The new rules:

- `FeeUpdateRequest` takes both fields. Each must be present and zero or more. The flat fee has
  at most 2 decimals, and the percentage fee is at most 1 (100%) with at most 6 decimals.
- An `@AssertTrue` check repeats `ck_assets_at_least_one`, so a zero/zero request is refused with
  a validation message instead of a database error.
- An unknown asset type returns ASS-404 (`AssetTypeNotFoundException`). This replaces the
  `IllegalArgumentException` that `getFeeByAssetType` used to throw.
- The route is under `/api/v1/admin/**`, so `ApiSecurityConfiguration` already requires
  `ROLE_ADMIN`. The contract change is additive: `admin-api.yaml` gains the `PUT` operation
  and `FeeUpdateRequest`, and the Angular admin client was regenerated.

## Consequences

- An order placed before the edit and filled after it settles with the fee it was quoted. One
  placed after the edit gets the new fee. The admin screen says "Changes apply to new orders." above the table.
- There is no record of who changed a fee or what it was before. If audit is needed, add an
  `asset_fee_history` table written in the same transaction as the update.
  Changing the lookup won't be necessary.
- Two admins editing the same row at the same time: the last write wins. There is no version
  column on `assets`.
