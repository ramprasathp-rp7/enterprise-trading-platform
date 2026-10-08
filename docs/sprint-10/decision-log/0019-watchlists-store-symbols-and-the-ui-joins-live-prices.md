# 0009 Watchlists store symbols only, and the UI joins each symbol to its live price

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | SarojaPandiyan (watchlists story owner) |

## Context

Customers need named lists of instruments they follow, each with a current price. The lists live
in the Trade REST API (`watchlist/` package, migration `013_create_watchlists.sql`), and the
contract is `Frontend/trading-ui/contracts/watchlist-api.yaml`.

The price is not ours. It comes from Fauxnance, either polled every 5 minutes or consumed from the
`market-data` Kafka topic, and lands in the in-memory `MarketDataCache`. Fauxnance's quota is
2000 requests a day. A watchlist row is written rarely: when it is created, renamed, or an item is
added or removed. The price changes every refresh. When the first version was committed, the live
price feed for the UI was not ready yet, so the page ran on dummy prices.

## Options considered

| Option | For | Against |
|---|---|---|
| Store the last price on `watchlist_items` and refresh it in the background | One call gives the UI the list and the prices | Copies the cache into Postgres. Rows are written on every refresh, not only when the customer changes the list. The stored price is stale by definition and needs its own "as of" |
| The watchlist API joins prices server-side and returns enriched items | One call for the UI. Fauxnance and the cache stay hidden | Ties the list endpoint to market-data availability, so a pricing outage breaks list management. It also duplicates the latest-quote endpoint |
| Store symbols only. The UI looks up each symbol through `GET /api/v1/market-data/quotes/{symbol}/latest` | The list API never depends on prices. One quote source serves watchlists, the order ticket and the instrument panel. The UI could ship on dummy prices and switch to live ones without a backend change | The UI makes one quote call per symbol, and has to render a "no price yet" state |

## Decision

Store symbols only, and let the UI join each symbol to the latest quote (now through
`LiveQuoteStore`). What decided it is that managing a list must keep working when pricing is down.
The server-side join would have made the list endpoint fail together with market data. The
quote endpoint reads the cache, so the extra calls cost no Fauxnance quota.

Rules that came with the tables:

- Names are trimmed, 1–60 characters, and unique per account ignoring case
  (`uq_watchlists_account_name` on `lower(name)`; WL-409).
- A symbol must exist in `instruments` (INS-404) and appears once per list (WL-409).
  Untradable instruments may still be watched.
- `watchlist_items.account_id` is denormalised from the parent list, so ownership checks and
  per-account reads need no join.
- Lists are hard-deleted and cascade to their items. Unlike orders and positions they carry no
  audit value.
- A list or item that belongs to another account is reported as WL-404, not 403. This is on top
  of the ACC-403 check every route already makes, so the response never confirms that another
  account's list exists.

## Consequences

- The watchlist page depends on `LiveQuoteStore` for prices. A symbol the cache has not quoted
  shows without a price rather than failing the page.
- Price alerts (0010) reuse the same cache, so an alert and the watchlist row it was created from
  always agree on the price.
- A watchlist cannot show price history from its own data. The charts use candles (0013) and the
  intraday quote history instead.
- Concurrent creates with the same name are caught by the unique index and reported as WL-409, so
  the existence check before the insert is a convenience, not the guarantee.
