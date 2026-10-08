# 0010 Price alerts are evaluated by polling the quote cache every 30 seconds, behind an in-memory index

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | SarojaPandiyan (price alerts story owner) |

## Context

A price alert tells a customer when an instrument crosses a level: ABOVE fires at
`price >= threshold`, BELOW at `price <= threshold`. Something has to compare every new quote with
every active alert.

Quotes reach the order service from one of two writers, chosen by `market-data.quote-source`:
Fauxnance polled every 5 minutes (the default), or the Kafka `market-data` topic. Both end in
`MarketDataCache`. Most quotes are for symbols nobody has an alert on, and an unchanged quote is
seen again on every pass. Orders and `trade-events` say nothing about what a customer wants to
watch.

## Options considered

| Option | For | Against |
|---|---|---|
| Consume the Kafka `market-data` topic in the alerts module | Reacts to each quote as it arrives | Only works when the quote source is `KAFKA`. With the default `FAUXNANCE` source nothing is published, so alerts would never fire |
| Hook into the cache write and evaluate inside it | Immediate, and works with both quote sources | Puts database work and notification calls on the thread that stores quotes. A slow or failing alert would hold up price updates for the whole service |
| A scheduled job reads the cache every 30 seconds. An in-memory `ActiveAlertIndex` filters out symbols nobody watches and quotes already evaluated before any database call | Works with both quote sources. Failures are isolated from the price feed. Most quotes cost nothing | An alert can fire up to 30 seconds after the quote arrived. The index has to be kept in step with the database |

## Decision

Poll `MarketDataCache` from `PriceAlertEvaluator` (`price-alerts.evaluate.interval-ms`, default
30000), with `ActiveAlertIndex` in front of the database. What separated it from the Kafka option
is that the default deployment has no `market-data` traffic. Compared with hooking into the cache
write, the poll keeps alert failures away from the price feed. The 30-second delay is small next
to the 5-minute quote refresh, so it changes nothing a customer would notice.

How the index works:

- `watched` is the set of symbols with an active alert. It is rebuilt from the database at the
  start of every run, and updated straight away when an alert is created or resumed.
- `lastSeen` is the newest quote time already evaluated per symbol. A quote that hasn't changed
  stops here.
- Only a quote that passes both reaches `findActiveBySymbol`. That query is served by the partial
  index `ix_price_alerts_active_symbol ON price_alerts (symbol) WHERE active`.
- Stale quotes and quotes without a price are skipped and marked evaluated.

## Consequences

- Alerts fire whatever the quote source is, and need no extra Fauxnance quota.
- An alert can fire up to one interval late. If near-real-time alerts are ever required, lower
  the interval or move to a cache listener. The evaluator's `onQuote` method is already the
  per-quote entry point either would call.
- The index lives in one JVM. If the Trade API is ever run as several instances, each would
  evaluate and fire the same alerts. Firing is idempotent per alert and quote (0011), which
  limits the damage, but evaluation would need a single leader.
- "Touching the level counts as crossing it" (`>=` and `<=`) is now part of the behaviour customers
  rely on. Changing it to a strict crossing needs a superseding entry.
