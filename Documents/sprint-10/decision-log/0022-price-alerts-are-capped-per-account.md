# 0012 Price alerts are capped at 20 active and 100 in total per account

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | SarojaPandiyan (price alerts story owner) |

## Context

Every watched quote is checked against every active alert for its symbol (0010). The cost of a run
grows with the number of active alerts, and every alert that fires is a notifier call. The
creation route is open to any signed-in customer, so with no limit one account could make the
evaluator the thing that slows the service down. Paused and triggered alerts are not evaluated,
but they and their trigger history stay in the tables until the customer deletes them.

## Options considered

| Option | For | Against |
|---|---|---|
| No limit | Nothing to explain to the customer | One account can grow evaluator work and table size without bound |
| One cap on all alerts | Simple | Either too tight for customers who keep a history of paused alerts, or too loose to bound evaluator work |
| Two configurable caps: active alerts (checked on create and on resume) and all alerts | Bounds evaluator work and storage separately. Each can be tuned per environment without a release | Two numbers to document. Resuming a paused alert can be refused |
| A per-account rate limit on the creation route | Stops bursts | Doesn't bound the number of alerts that end up active, which is what costs evaluation time |

## Decision

Two caps. `price-alerts.max-active-per-account` defaults to 20 and
`price-alerts.max-per-account` to 100 (env `PRICE_ALERTS_MAX_ACTIVE_PER_ACCOUNT` and
`PRICE_ALERTS_MAX_PER_ACCOUNT`). Going over either returns ALR-409. What decided it over a single
cap is that evaluation cost depends only on active alerts, while the total only costs storage. A
single number can't bound one without starving the other.

Validation that goes with the caps:

- The threshold must be greater than 0 and less than 10^12. The table repeats this with
  `ck_price_alerts_threshold_positive`.
- The same active rule (account, symbol, direction, threshold, mode) can't exist twice. The
  partial unique index `uq_price_alerts_active_rule` enforces this, and a duplicate is ALR-409.

## Consequences

- The cap check counts and then inserts without a lock, so two creates at the same moment can go
  one over the cap. We accepted that because one extra alert costs nothing measurable. If a hard
  limit is ever needed, take an advisory lock on the account id or count inside the insert.
- Resuming a paused alert can fail with ALR-409. The message tells the customer to pause or delete
  another alert first.
- Deleting an alert also deletes its trigger history (`ON DELETE CASCADE`). Deleting is how a
  customer frees room under the 100 limit.
