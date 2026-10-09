# 0010 Quotes refresh every five minutes, which keeps the Fauxnance quota for order fills

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | raahulkumardebug (live prices, market data) |

## Context

Fauxnance allows 2,000 requests per day, resetting at 00:00 UTC. The budget is shared between the
quote refreshes and the executor's order-fill path, which fetches one quote to price every fill. A
request costs one unit however many symbols it carries, up to 25 per batch, and the quote universe was
25 symbols when this was decided (the FX pairs that convert currencies are added to the same list).

The executor-service poller defaulted to 60 seconds. At that rate one poller alone uses
86,400 / 60 = 1,440 requests a day, leaving 560 for fills. The Trade API's new refresh
(decision 0009) is a second caller of the same API, so two pollers at 60 seconds would need 2,880
requests a day and exhaust the quota in about 16.6 hours (the poller design doc works this through as
its "aggressive" example, with the same 2,880 figure). An exhausted quota stops orders filling, which is worse than a stale
watchlist price.

## Options considered

| Option | For | Against |
|---|---|---|
| 60 seconds (the existing poller default) | Freshest prices | Two callers at this rate exceed 2,000 a day. One caller leaves only 560 requests for fills |
| 120 seconds | 720 requests a day per caller | Two callers still use 1,440, leaving 560 for fills and valuation fallbacks |
| 300 seconds (five minutes) | 288 requests a day per caller for up to 25 symbols, so two callers use 576 and leave about 1,400 | Prices up to five minutes old |
| Fetch on demand when a request needs a price | Always current | Quota use follows page views, not time. A dashboard polling would spend it unpredictably |

## Decision

Both quote callers poll every five minutes: `market-data.refresh.interval-ms=300000` in the Trade API
and `POLL_INTERVAL_SECONDS=300` in executor-service (changed from 60). The deciding number is quota:
five minutes is the interval at which two callers still leave most of the 2,000 requests for fills,
and the cost is fixed by the clock instead of by how many people have the app open.

The whole symbol list is sent in batched calls of up to 25, so a list of 25 or fewer costs one request
per refresh. The frontend's `LiveQuoteStore` uses the same five-minute TTL, so a browser reads the cache
and never causes a Fauxnance call.

## Consequences

- Every price is up to five minutes old. Alerts, advice and strategies all see at most one new quote
  per symbol every five minutes, so `EVERY_QUOTE` alerts fire at most that often.
- The intraday chart has at most 288 points a day per symbol.
- The poller's minimum stays at 60 seconds (enforced in code, with a warning), so nobody can set it to
  a value that breaks the budget by accident.
- More than 25 symbols needs a second batch, which doubles the refresh to 576 requests a day per
  caller. The conversion FX pairs count towards the 25, so re-do the arithmetic in
  `Services/order-service/design/market-data-poller.md` before adding instruments or currencies.
- If prices need to be fresher, shorten the interval on one caller only (the cache has one writer, see 0009).
