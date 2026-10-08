# 0009 The quote cache has exactly one writer, picked by `market-data.quote-source`

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | raahulkumardebug (live prices, market data) |

## Context

Watchlists, price alerts, the trade advisor, strategies and P&L all read "the current price" from
`MarketDataCache` in the Trade REST API. Before this change the only way quotes reached it was the
executor-service's `MarketDataPoller` publishing `QUOTE` events to the `market-data` Kafka topic, and
`MarketDataConsumer` writing them into the cache. That meant the Trade API showed no prices unless
executor-service and Kafka were both up and publishing.

The cache is also not a simple "latest value per symbol" map any more: every quote is appended to a
per-symbol history (24 hours by default) that alerts, the intraday chart and strategy indicators read.
Appending the same quote twice, or two sources interleaving, would put duplicates and out-of-order
points into that history.

## Options considered

| Option | For | Against |
|---|---|---|
| Kafka only (as before) | No Fauxnance code in the Trade API. One producer | The Trade API shows nothing without executor-service. The Angular app cannot be demonstrated against the Trade API alone |
| Fauxnance only | Simplest. Works with just the Trade API and the quotes API | Throws away the Kafka path the executor already publishes to, and the `market-data` topic stops having a consumer |
| Both writers, always on | Most data. Either one can fail | Every quote lands twice, so history has duplicates. Quota is spent twice for the same symbols. No rule for which write wins |
| One writer, chosen by configuration | No duplicates. Either path can be demonstrated. Default needs no Kafka producer | Switching needs a restart. Someone has to pick correctly |

## Decision

`market-data.quote-source` (env `MARKET_DATA_QUOTE_SOURCE`) selects `FAUXNANCE` (default) or `KAFKA`, and
only the writer for that source is created: `MarketDataRefreshScheduler` or `MarketDataConsumer`, each
behind `@ConditionalOnProperty`. The cache therefore never has two writers.

What separated it from running both is the history. A single writer keeps it strictly ordered and
duplicate-free without any de-duplication code, and a wrong setting is visible straight away (no
prices) rather than silently doubling traffic.

`QuoteSourceConfig` binds the property to the `QuoteSource` enum at startup. The writers switch on the
raw string, so without that a typo such as `KAFKA ` would create neither writer and the cache would
sit empty. With it, an unknown value stops the service from starting.

## Consequences

- Reads are identical in both modes: `GET /api/v1/market-data/quotes/{symbol}/latest` and the
  intraday endpoint read the cache and never call Fauxnance, so the UI does not care which writer
  is active.
- In `KAFKA` mode the Trade API depends on the executor-service poller being up. In `FAUXNANCE` mode it
  spends its own Fauxnance quota (see 0010).
- The quote source can only change by restarting the service.
- Both writers produce the same `QuotePayload` fields, so a consumer of the cache cannot tell them apart.
- `MarketDataRefreshScheduler` also quotes the FX pairs needed for conversion (decision 0008), so
  those come in through the same writer.
