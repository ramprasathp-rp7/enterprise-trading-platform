# 0035 Symbol discovery lists any symbol the Fauxnance registry knows and can quote, as an ordinary instrument

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-09 |
| Decided by | raahulkumardebug (instruments, market data) |

## Context

The platform traded a fixed list of 25 seeded instruments, and the Angular app kept its own copy of that list
in code. Customers wanted to look up a symbol that is not listed (for example SHOP or TCS.NS) and, if market
data exists for it, trade it like any other. Fauxnance has a symbol registry (`GET /symbols/{symbol}`) that
runs an exact upstream discovery and returns the name, type, exchange (NYSE, NASDAQ, NSE, BSE, FX, CRYPTO),
currency and whether it is active. The quote refresh and the executor's poller already quote every symbol in
the `instruments` table, and the daily quota is shared with order fills (decision 0010).

## Options considered

| Option | For | Against |
|---|---|---|
| Keep the fixed list | No quota risk | No way to add an instrument without a migration |
| Look symbols up on every search, without storing them | Nothing persisted | Spends quota per keystroke; an order for a symbol not in `instruments` is rejected anyway |
| Add found symbols to `instruments` and the cache | One lookup per new symbol; from then on it is an ordinary instrument that orders, alerts, charts and polling already handle | Every added symbol is quoted on every refresh for good, and past 25 symbols a refresh needs a second batch |

## Decision

`POST /api/v1/instruments/discover` looks the symbol up in the registry and asks for one quote (two quota
units). When both answer, the instrument is inserted with the registry's name, asset class, currency,
exchange and active flag, its quote goes into the cache straight away, and its daily candles load in the
background. A symbol already listed is returned without calling Fauxnance. The Angular catalogue now loads
from `GET /api/v1/instruments` instead of its own copy, and discovery is offered wherever instrument search
finds nothing, as well as on the Instruments page.

A new `instruments.exchange` column (migration 026) holds the registry's exchange, so the USA / NSE market
preference (decision 0036) can filter on it. Seeded rows are backfilled from their symbols.

## Consequences

- Any signed-in user can add instruments. Unknown symbols cost two quota units and add nothing.
- Each added symbol makes the instrument list longer for everyone. Past 25 symbols, the refresh and the
  poller each need a second batch, doubling their quota use (decision 0010). Watch the count; a cap or an
  admin-only discovery is the next step if quota becomes tight.
- A symbol in a new currency gets its conversion FX pair added to the refresh automatically, but orders for
  it are rejected until the first refresh has fetched that rate.
- Inactive registry entries are listed as not tradable.
