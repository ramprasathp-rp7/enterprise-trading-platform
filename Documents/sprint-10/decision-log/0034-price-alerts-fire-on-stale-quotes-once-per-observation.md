# 0034 Price alerts fire on stale quotes too, at most once per observation

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-09 |
| Decided by | raahulkumardebug (watchlists and alerts) |

## Context

`PriceAlertEvaluator` skipped any quote Fauxnance marked `stale`. Fauxnance marks a quote stale whenever it
serves its own cached price, which is every quote while a market is closed. In practice that was most of
the day: at 08:58 UTC on 8 October, 21 of the 25 cached symbols were stale, every US and Indian stock
included. A customer who set "AAPL above 333" while AAPL sat at 336.67 never heard about it, because the
only price available was the last close. The team asked for alerts to fire even when the data is stale.

Stale quotes have a second property: the same observation (same `quoteAsOf`) arrives again on every
five-minute refresh (decision 0010), each time with a new receive time. The evaluator identified a quote
by its receive time.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep ignoring stale quotes | Never alerts on an old price | Alerts are silent whenever markets are closed, which is what the team reported as broken |
| Evaluate stale quotes like fresh ones | Smallest change | An EVERY_QUOTE alert would fire again every five minutes on the same unchanged closing price, all night |
| Evaluate stale quotes, keyed on when the price was observed | Fires once on the last close; fires again only when a genuinely new price arrives | A stale fire is keyed on an older time than a fresh one, so the two clocks must not be confused |

## Decision

Stale quotes are evaluated. A stale quote is identified by its `quoteAsOf` (falling back to the receive time),
so repeated deliveries of the same closing price are one observation, and the existing rule "never twice for
the same quote" stops EVERY_QUOTE from repeating. Fresh quotes keep their receive time as before, so nothing
changes while markets are open. The firing's reason says so, e.g. `AAPL 336.67 >= 333 (ABOVE, ONCE, stale quote)`,
and the trigger's `quote_at` records the observation time.

## Consequences

- Alerts set while a market is closed fire on its last price, and again only when the market produces a new one.
- The reason text tells the customer the price was a stale quote, and the Notifications page shows it.
- `index.needsEvaluation` still uses the receive time, so each refresh is looked at, but most refreshes of a
  stale symbol end at the per-alert "not newer" check without a database write.
- An alert that already fired on a fresh price is not re-fired by an older stale observation.
