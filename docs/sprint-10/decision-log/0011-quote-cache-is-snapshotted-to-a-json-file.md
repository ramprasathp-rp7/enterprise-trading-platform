# 0011 The quote cache is snapshotted to a JSON file and restored on startup

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | raahulkumardebug (live prices, market data) |

## Context

`MarketDataCache` lived only in memory, so every restart of the Trade API emptied it. That cost more
than a gap in a chart. History is built one five-minute quote at a time (decision 0010), and the
refresh only ever appends the current quote, so nothing can refill what a restart loses. Strategies
need that history: a moving-average strategy with a 100-point window needs about eight hours of
five-minute quotes before it can evaluate, and every restart started that clock again. The intraday
chart loses the same data.

The service is restarted often during development and demonstration, and the first version of the
cache also cleared itself every day.

## Options considered

| Option | For | Against |
|---|---|---|
| Memory only (as before) | No new code | Every restart empties the history. Strategies wait hours to evaluate again |
| A Postgres table | Survives restarts. Visible in SQL | A write for every quote of every symbol. A migration, a mapper, and database traffic on the hot path of a cache |
| An external cache such as Redis | Built for this. Shared between instances | A new piece of infrastructure the stack does not have, for a single instance |
| A JSON snapshot file | No infrastructure. One file to inspect, copy or delete. Cheap because it writes only when the cache changed | Single instance only. A crash loses up to one interval |

## Decision

`MarketDataCacheFileStore` snapshots the cache to `data/market-data-cache.json` and restores it on
startup. A file won over a database because the only thing the cache must survive is a restart of
one process, and a file is the smallest thing that does that.

How it behaves:

- **Restore** runs in `@PostConstruct`, before the scheduled refresh and the Kafka listener start
  writing, so restored quotes are always older than live ones and the history stays in order.
- **Save** runs every `market-data.cache.persist-interval-ms` (default 60 seconds) and only if the cache's
  version counter has moved, plus once on shutdown.
- **Atomic write.** The snapshot goes to a temp file and is moved into place, so a crash mid-save never
  leaves a half-written file.
- **Unreadable file.** It is logged and ignored, and the cache starts empty. It never stops the service.
- **Wipe.** `MARKET_DATA_CACHE_WIPE_ON_STARTUP=true` deletes the file and starts empty. Set it back to
  `false` afterwards.
- **Switch off** with `MARKET_DATA_CACHE_PERSISTENCE_ENABLED=false`.

## Consequences

- A clean stop loses nothing and a crash loses at most 60 seconds of quotes.
- The file holds at most the history window (24 hours by default) for every symbol, roughly 288 quotes
  per symbol.
- It is a single-instance design. Two Trade API instances would each keep their own file and diverge.
  A shared cache would need Redis or a table, and a superseding entry.
- `Services/order-service/data/` is in `.gitignore`. In a container the folder needs a volume, or the
  snapshot goes with the container.
- Restored quotes can be old. Anything that needs a current price must check age (decision 0012 does).
