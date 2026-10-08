# 0012 Valuation reads the cache first and calls Fauxnance only for quotes older than ten minutes

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | raahulkumardebug (live prices, market data) |

## Context

Portfolio valuation and FX rate lookups (decision 0008) need a quote for every instrument held and for
the FX pair that converts it, in one request. The dashboard and Positions pages request them on every load.
If each call went to Fauxnance, quota use would follow page views instead of the clock, which is what
decision 0010 set out to avoid. The cache already holds those quotes, refreshed every five minutes.

A restored cache (decision 0011) can also hold quotes that are hours old, so "it is in the cache"
does not mean "it is current".

## Options considered

| Option | For | Against |
|---|---|---|
| Always call Fauxnance | Always current | Quota spent on every page load. A busy dashboard could starve order fills |
| Cache only, no Fauxnance call | Zero quota. Simplest | A symbol missing from the cache, such as a new FX pair, cannot be valued. A stale restored quote is used silently |
| Cache first, one batched Fauxnance call for the rest, old cache as a last resort | Normal requests cost nothing. A missing or old quote is still fetched. Something is returned even if Fauxnance is down | A three-step order to understand and test |

## Decision

`QuoteResolver` resolves a set of symbols in three steps:

1. Use the cache for each symbol whose newest quote was received within `market-data.quote-resolver.max-age`
   (default `PT10M`).
2. Make one batched Fauxnance call for the symbols that are missing or older than that.
3. For anything Fauxnance did not return, use the older cached quote and mark it `stale`.

Ten minutes is two refresh intervals: a single missed refresh does not trigger a Fauxnance call, but a
quote that has missed two is treated as no longer current. A valuation and the FX pairs it needs are resolved
in the same call, so they are priced together.

## Consequences

- The normal path costs no quota, because the refresh scheduler keeps the cache inside the ten-minute window.
- If the service was down for more than ten minutes, the restored quotes are too old to use, so the first
  valuation makes one batched call and the cache is current again after that.
- A valuation can use a quote up to ten minutes old. `ResolvedQuote.stale` marks quotes older than that, and
  quotes Fauxnance itself flagged stale.
- If `max-age` is set below the refresh interval, every valuation will call Fauxnance. Keep it at two
  intervals or more when changing decision 0010.
