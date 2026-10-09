# 0008 Orders trade in the instrument's currency and settle in the account's, at a rate fetched fresh at fill

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Ram Prasath P (portfolio and P&L story owner) |

## Context

The portfolio contract (`portfolio-api.yaml`, `PortfolioSummary.baseCurrency`) says holdings in another
currency must be converted before they are summed, using Fauxnance `FX:` pairs. The other permitted
option is to restrict the instrument universe to one currency and say so.

Before this change, currency was only a label. Every account is USD, but the instrument table includes
INR stocks (INFY.NS, RELIANCE.NS, TATASTEEL.BO) and an INR- and a JPY-quoted FX pair. Fauxnance can
return any currency. Rupees were added to dollars 1:1 in all of these places:

- portfolio totals and P&L
- the end-of-day net worth (and the seeded history)
- the funds check and the cash reserved for NEW orders
- settlement and the fee

The final coaching notes list "INR is summed into USD" as a defect.

There are no market hours. The fill rule fills or rejects at once. But an order does wait in NEW while
the executor is stopped, which is how the team demonstrates a market close, and Cancel exists for that
case. So placement and fill can be minutes or hours apart.

## Options considered

| Option | For | Against |
|---|---|---|
| USD-only instruments, recorded here | Smallest change; the contract allows it | Removes instruments that are already seeded and held. It is a workaround, not multi-currency support |
| Convert, one rate captured at placement and reused at fill | Simplest. The executor has no FX code. A BUY can never debit more than it reserved | With the executor stopped, a fill hours later settles at a stale rate |
| Convert, placement rate for the funds check and reservation, fresh rate fetched by the executor at fill for settlement | Settlement uses the rate at the moment of the trade, which is correct for the market-close demo. The FX pair is fetched in the same quote batch, so it costs no extra Fauxnance quota | The executor needs FX lookups and a second column. The rate can move between reservation and fill, so the fill needs a funds re-check |

## Decision

Convert, using the placement rate for the funds check and the reservation, and a fresh rate fetched by
the executor at fill for settlement. The fill rate is the rate of record.

What separated it from a single placement rate is that orders do wait in NEW, so the single-rate
option settles at a rate that can be hours old. The single-rate option's best property, that a fill
can never overdraw, is kept another way: the executor's existing `canAfford` check now runs at the
fill rate and rejects with "Insufficient funds" if the rate has moved against the customer.

The rules:

| Concept | Currency |
|---|---|
| Limit price, executed price, average cost per unit, quotes, candles, watchlists, alerts | instrument |
| Funds check and reservation: `price x qty x placement_fx_rate + fee` | account |
| Settlement: `executed x qty x fill_fx_rate` | account |
| Fee: `flatFee + pct x account-currency notional` (the fee schedule is USD) | account |
| Transactions (BUY, SELL, FEE, DEPOSIT, WITHDRAW) | account |
| Realised P&L, booked at the SELL fill: `qty x (executed x fill_fx_rate - average_cost_account)` | account |
| Unrealised P&L and portfolio totals, at the live rate | account |
| Net-worth history, at the snapshot's rate | account |

A rate is account-currency units per instrument-currency unit. Lookup order:

1. The same currency gives 1.
2. Otherwise use `FX:{instrument}{account}` (its price).
3. Otherwise use `FX:{account}{instrument}` (one over its price).

If neither pair is available, the order is rejected at placement, the executor treats it as a pricing
outage at fill (the existing retry and reject path), and the portfolio total is marked `partial`. The
quote's currency wins over `instruments.currency` if they disagree, so a new Fauxnance currency works
without a code change. All accounts are USD, but nothing hard-codes USD: the code converts to
`account.currency`.

## Consequences

- **Schema:** migration 022 (`022_multi_currency.sql`) adds these columns:
  - `orders.placement_fx_rate`, `orders.fill_fx_rate` and `orders.realised_pnl`
  - `positions.average_cost_account`
  - CHECKs that every currency is a three-letter code
- **Seeds:** use the reference rate FX:USDINR = 83.47. The net-worth history of ACC-000001 and
  ACC-000005 is recomputed in USD.
- **Positions:** a position keeps two average costs:
  - instrument currency, for the price comparison the contract defines
  - account currency, at the fill rates of the buys
  So unrealised and realised P&L in account currency include FX gains and losses.
- **Realised P&L:** it is stored on the order when the SELL fills and is never recomputed. This
  replaces the earlier stand-in (`executed - limit price`).
- **Admin analytics:** the totals are correct because every transaction is now in USD.
- **Analytics (ETL):**
  - `fact_trades.trade_value` stays in instrument currency, which is right for exposure by instrument.
  - `trade_value_account` and `fx_rate` are added. Volume and average trade size must sum
    `trade_value_account`.
  - Reconciliation checks both totals.
- **Market data:** the pollers also quote `FX:{account}{instrument}` for every currency in use, so the
  quote cache has a rate even when the pair is not a tradable instrument.
- **No reservation buffer:** the reservation has no FX buffer. Under a long executor outage an order
  can therefore be rejected at fill even though it passed the placement check. That rejection is the
  accepted cost.
- **Revisit if** market hours or resting (good-till-cancelled) orders are introduced. Then reserve with
  a buffer, for example 2%, as well as re-checking at fill.
- **Non-USD accounts:** if accounts in other currencies are introduced, the admin analytics totals,
  which sum across accounts, must be grouped by account currency.

## Contract changes

This decision changes three binding contracts. Every change is additive: no field was removed, renamed
or retyped, and no existing field changed meaning. So a consumer written against the earlier contract
keeps working, and only consumers that want the account-currency figures need to read the new fields.

| Contract | Added | Why it is additive |
|---|---|---|
| `Frontend/trading-ui/contracts/portfolio-api.yaml`, `PricedPosition` | `fxRate` (nullable), `averageCostAccount`, `costBasisAccount`, `marketValueAccount` (nullable), `unrealisedPnlAccount` (nullable) | The existing `averageCost`, `costBasis`, `lastPrice`, `marketValue` and `unrealisedPnl` stay in the instrument's currency, as the contract already defined them. The summary totals were always in `baseCurrency`. They are now the sum of the `*Account` fields, which is the conversion the contract asked for, rather than a 1:1 sum of mixed currencies |
| `analytics-schema.sql`, `fact_trades` (both copies: `Database/duckdb/analytics/` and `ETL/src/trading_analytics_etl/sql/`) | `fx_rate DECIMAL(20,10) NOT NULL DEFAULT 1`, `trade_value_account DECIMAL(18,2) NOT NULL` | `trade_value` keeps its meaning (instrument currency). `ETL/.../load.py` adds both columns to stores created before them, so existing DuckDB files load without being rebuilt. Volume and average-trade-size queries move to `trade_value_account`, because summing `trade_value` across currencies was the defect |
| `trade-events` payload (`TradeEventPayload`, documented in `Services/order-service/design/kafka.md`) | `fillFxRate` (optional) | Absent or null means 1, the same-currency rate. So events published before the change, and producers that do not set it (the Trade API's `ORDER_CANCELLED`), are still valid. Consumers that ignore unknown fields are unaffected |

The generated Angular portfolio client was regenerated from the updated `portfolio-api.yaml`, so the new
fields are typed in the UI.
