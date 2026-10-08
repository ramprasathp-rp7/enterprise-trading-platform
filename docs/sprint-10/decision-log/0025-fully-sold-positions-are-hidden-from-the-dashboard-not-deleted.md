# 0015 A fully sold position keeps its row, and the dashboard hides holdings at quantity 0

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |
| Decided by | SarojaPandiyan (dashboard holdings) |

## Context

When a SELL fills the whole quantity, the executor sets the position to quantity 0. It doesn't
delete the row. The row still holds `average_cost`, `average_cost_account` and a `version` used
for optimistic locking, and a later BUY of the same instrument updates that same row. Before this
fix, the dashboard's positions overview listed every row returned by
`GET /api/v1/accounts/{id}/positions`. Instruments the customer had sold out of therefore showed
as holdings with quantity 0, and were passed on to the allocation bar.

## Options considered

| Option | For | Against |
|---|---|---|
| Delete the position row when quantity reaches 0 | Every consumer sees only open holdings | Changes the executor's write path and its optimistic-locking update into a delete, so a re-buy becomes an insert. That risk lands on settlement to fix a display problem |
| Filter `quantity > 0` in the positions API | One fix for every client | Changes what an existing contract returns. Other consumers, such as admin views and tests, may rely on seeing closed positions |
| Filter `quantity > 0` in the dashboard's positions overview | Fixes what the customer sees without touching settlement or a contract | Each other screen that lists holdings has to apply the same rule |

## Decision

Filter in `positions-overview.component.ts`. The filtered list is the one it shows and the one it
emits as `positionsLoaded`, so the allocation bar gets the same holdings. What decided it is the
size of the risk. Settlement and the positions contract are shared, and this was a display bug
found on the last day of the sprint. The portfolio routes already price only open quantities, so
the totals were never wrong.

## Consequences

- The dashboard and the allocation bar show only open holdings. A spec covers the quantity-0 case.
- Closed positions are still in the database and in the raw positions API, which keeps the data
  that realised P&L history and a re-buy need.
- Any new screen that lists holdings from the raw positions route must filter the same way. If a
  third screen needs it, move the filter into the API as an opt-in `?open=true` parameter rather
  than changing the default.
