# Sprint 10 features

Sprint 10 added seven features to the Enterprise Trading Platform:

1. [Watchlists and price alerts](#1-watchlists-and-price-alerts)
2. [Notifications (order outcomes)](#2-notifications-order-outcomes)
3. [Customer preferences (alert channels)](#3-customer-preferences-alert-channels)
4. [Portfolio and P&L](#4-portfolio-and-pl)
5. [Graphs](#5-graphs)
6. [Trade advisor](#6-trade-advisor)
7. [Strategy executor (automated trading)](#7-strategy-executor-automated-trading)

All of them run inside the Trade REST API (`Services/order-service`, port 8081) and the Angular app (`Frontend/trading-ui`). Most of them depend on one shared piece, [live prices](#0-shared-foundation-live-prices), so that is described first.

Related documents in this folder:

- [`decision-log/`](decision-log/): the design decisions behind preferences and notifications
  (0001–0007), multi-currency (0008), watchlists and price alerts (0009–0012), candle data (0013),
  admin fee editing (0014), dashboard holdings (0015) and migration numbering (0016).
- [`decision-log/`](decision-log/): the design decisions behind preferences, multi-currency, live prices (0009 to 0012), notifications (0013, 0014), the trade advisor (0015), the generated API clients (0016), the browser's notification state and admin preferences (0017, 0018), platform configuration, account snapshots and P&L booking (0031 to 0033), and price alerts on stale quotes, symbol discovery and trading settings (0034 to 0036).
- [`security-review/`](security-review/): the combined OWASP review of these modules.

## Conventions every feature follows

- **Contract first.** Each API is written as OpenAPI in `Frontend/trading-ui/contracts/*.yaml` before its controller. `npm run generate:<name>` produces the typed Angular client in `src/app/api/generated/`, so a renamed field breaks the build instead of the page.
- **Account check on every route.** Each controller calls `AccountAccessAuthorizer.assertTokenCanAccess(accountId)`. A token whose `accountId` claim doesn't match the path account gets `403 ACC-403`.
- **Data access.** MyBatis mappers (annotations, every value bound with `#{}`) talk to Postgres. Tables come from the numbered migrations in `Database/postgres/order-service/migrations/`.

## 0. Shared foundation: live prices

```
Fauxnance quotes API ──(every 5 min)──► MarketDataRefreshScheduler ──┐
                                                                      ├──► MarketDataCache (in memory, 24h of quotes per symbol)
executor-service poller ──► Kafka "market-data" ──► MarketDataConsumer┘           │
                                                                                  ├──► GET /api/v1/market-data/quotes/{symbol}/latest
                                                                                  └──► read directly by alerts, advice, strategies, P&L
```

- **Where quotes come from.** `market-data.quote-source` picks one writer: `FAUXNANCE` (the default) polls the API, and `KAFKA` consumes the `market-data` topic.
- **The cache.** `MarketDataCache` is the single source of "current price" for the whole service. It is also restored from a JSON file at start-up, so a restart doesn't begin empty.
- **Frontend.** `LiveQuoteStore` (`core/market-data/live-quote.store.ts`) calls the latest-quote endpoint through the generated client and refreshes every 5 minutes. Watchlists, the order ticket and the instrument panel read their prices from it.

## 1. Watchlists and price alerts

**What it does.** Customers keep named lists of instruments with live prices, and can ask to be told when a price goes above or below a level.

### Backend

Code is in `watchlist/` and `watchlist/alerts/`. Each has its own README.

- **Watchlists.** `WatchlistController` at `/api/v1/accounts/{id}/watchlists`, with tables `watchlists` and `watchlist_items`. A list stores symbols only; the price is joined in the UI.
- **Alerts.** `PriceAlertController` at `/api/v1/accounts/{id}/price-alerts`, with tables `price_alerts` and `price_alert_triggers`.
- **Two modes.** `ONCE` fires and then switches the alert off. `EVERY_QUOTE` fires on every new quote past the level.
- **Cap.** 20 active alerts and 100 in total per account (`ALR-409` beyond that).
- **Evaluation.** `PriceAlertEvaluator` runs every 30 seconds against `MarketDataCache`. `ActiveAlertIndex` skips symbols nobody watches and quotes already seen, so most quotes never reach the database. "Crossed" means price ≥ level for ABOVE, and price ≤ level for BELOW.
- **When an alert fires:**
  1. A trigger row is written with its reason, e.g. `AAPL 191.2 >= 190 (ABOVE, ONCE)`.
  2. The trigger is handed to `PreferenceAwareAlertNotifier`.
  3. The notifier resolves the customer's channels (see [feature 3](#3-customer-preferences-alert-channels)), marks the trigger delivered and `in_app`, and sends email or SMS through the shared `NotificationDispatcher`.
  4. If any step throws, the trigger is marked `FAILED`, the alert stays on, and the same quote is retried on the next run.

### Frontend

Code is in `features/watchlist/`.

- The Watchlists page shows your lists and the selected list's instruments with live prices.
- Each row has a bell button (`price-alert-button`). It opens the alert form, with the threshold prefilled from the live price.
- The Price alerts panel lists your alerts, with pause/resume, delete and trigger history.
- `PriceAlertWatcherService` polls `/price-alerts/triggers?since=…` every 30 seconds and pushes new in-app triggers into the notification bell.

### Flow

Create an alert → the evaluator sees a quote past the level → the trigger is recorded → channels are resolved → bell, email or SMS → a `ONCE` alert turns off.

## 2. Notifications (order outcomes)

**What it does.** Tells the customer when an order is filled, rejected or cancelled, on the channels they chose.

### Backend

Code is in `notifications/`.

- **Consumer.** `TradeEventNotificationConsumer` listens to the Kafka topic `trade-events`, which the executor-service publishes after handling an order.
- **For each `ORDER_FILLED`, `ORDER_REJECTED` or `ORDER_CANCELLED` event:**
  1. Parse it, and fill in any missing symbol, side or quantity from the order table.
  2. Ask `NotificationDispatcher` for the customer's channels. It uses the preferences module's `PreferenceResolver`, and falls back to in-app if preferences can't be read.
  3. Store the event in `order_notifications`. `event_id` is unique, so a redelivered event is stored once.
  4. On the first store only, send EMAIL and SMS through the `ChannelSender` implementations. These are stubs today: they log the message with the address masked, and nothing is delivered. A redelivery never sends twice.
- **Read route.** `GET /api/v1/accounts/{id}/order-notifications` returns the in-app rows.
- **Shared dispatcher.** Price alerts send through the same `NotificationDispatcher`.

### Frontend

Code is in `core/notifications/`.

- `OrderStatusWatcherService` polls the order-notifications feed. It keeps a cursor (the last id seen) in localStorage, so an account's history isn't replayed on first login.
- `NotificationService` keeps the bell's items in localStorage per user, newest first, capped at 50 and de-duplicated by key.
- `notification-bell.component` renders the unread badge and the panel.

### Flow

Place an order → `orders` topic → the executor fills or rejects it → `trade-events` → the consumer stores the row and resolves channels → the bell's poll shows it, and the email and SMS stubs log it.

## 3. Customer preferences (alert channels)

**What it does.** The customer chooses how the platform reaches them: one to three of EMAIL, SMS and IN_APP, each with its contact detail.

### Backend

Code is in `preferences/`; see its README and decisions 0001–0007 in [`decision-log/`](decision-log/).

- **Customer routes.** `PreferencesController`: `GET`, `PUT` and `DELETE /api/v1/accounts/{id}/preferences`. It sits in `preferences.internal.web`, which is private to the module.
- **Validation** (`ContactDetailRules`). EMAIL needs an email address. SMS needs an E.164 number; spaces and dashes are stripped. IN_APP takes no detail. A detail that doesn't fit its channel returns `422 PREF-422`.
- **Storage.** Table `customer_preferences`. Contact details are stored only as AES-256-GCM ciphertext bound to the account id (`ContactDetailCipher`, key `PREFERENCES_CONTACT_KEY`).
- **Internal API.** Other modules call `PreferenceResolver.resolveAlertChannels(accountId)` in-process. There is deliberately no HTTP route for it, so a customer token can never read decrypted contact details. An ArchUnit test (`PreferencesModuleBoundaryTest`) enforces that boundary.
- **Fallbacks.** No stored rows give the IN_APP default. If the store is down, the resolver returns the last known answer marked `stale`, or raises an error if it has none.

### Frontend

The Settings page (`features/settings/`) uses `preferences.store.ts` to get, update and reset preferences.

### Flow

The customer saves channels → they are validated, encrypted and stored → every notification and alert resolves them at send time, so a change applies to the next message.

## 4. Portfolio and P&L

**What it does.** Values the customer's holdings and reports their profit and loss.

### Backend

`PortfolioController` routes:

| Route | Returns |
|---|---|
| `GET /api/v1/portfolio/{id}` | Summary: cash, market value, cost basis, P&L totals |
| `GET /api/v1/portfolio/{id}/positions?symbol=` | Priced holdings, optionally for one symbol |
| `GET /api/v1/portfolio/{id}/pnl?from=&to=&bySymbol=` | Realised and unrealised P&L, optionally per symbol |

`PortfolioPnlService` prices each position from the live quote and converts it to the account's currency at the live FX rate (`FX:` pairs):

- **Market value** = quantity × price × FX rate.
- **Unrealised P&L** = market value − cost basis. Cost basis is built at the FX rate of each fill.
- **Total value** = cash + market value.
- A holding with no price or no FX rate is left out of the totals, and the response is flagged `partial`.

`RealisedPnlCalculator` books realised P&L when a SELL fills: quantity sold × (fill price × FX rate − average cost). The figure is stored on the order and never recomputed, so it can be summed per date range or per symbol.

### Frontend

The dashboard's portfolio header and balance summary, and the Positions page, read these routes through the generated portfolio client.

## 5. Graphs

There are three charts, each with its own data source.

| Chart | Frontend | Data | Backend |
|---|---|---|---|
| **Net worth** (dashboard) | `net-worth-chart.component` (Chart.js) | Daily account snapshots, with today's live portfolio value as the last point | `AccountHistoryScheduler` snapshots every account at 23:59 UTC into `account_history`. Read with `GET /api/v1/accounts/{id}/history` (up to 365 points) and `/history/range`. Admins can force a snapshot with `POST /api/v1/admin/account-history/snapshot` |
| **Candlestick** (instrument panel) | `instrument-chart.component` (SVG candles or a close line, plus volume), via `candle.service.ts` | Daily OHLC candles | `CandleSyncScheduler` loads history on start-up, syncs each day at 01:00, retries failures at 02:00, and purges candles older than 90 days on Mondays at 03:00. Candles are stored in `candle_data`. `CandleChartController` serves `GET /api/v1/instruments/{id}/candles?from=&to=&interval=`, `/latest` and `/{date}`. `CandleAggregationService` rolls daily candles into longer intervals |
| **Intraday quotes** (instrument panel) | `quotes-chart.component` (SVG line with a previous-close baseline and a bid/ask tooltip), via `quote-intraday.service.ts` | Today's cached quotes, one point per refresh | `GET /api/v1/market-data/quotes?symbol=` reads `MarketDataCache` |

Customer requests only read stored data. Candles are fetched from Fauxnance by the scheduler, never by a request.

## 6. Trade advisor

**What it does.** Gives a BUY, SELL or HOLD signal, with the reasons, for every instrument the customer holds or watches. It is educational, not investment advice.

### Backend

Code is in `advice/`.

- **Route.** `TradeAdviceController`: `GET /api/v1/accounts/{id}/advice`. Advice is computed on each request; nothing is stored.
- **Inputs gathered by `TradeAdviceService`:**
  - the customer's positions (quantity and average cost) and watchlist symbols;
  - for each instrument, the last 120 daily closes from `candle_data`;
  - the live price from `MarketDataCache`.
- **Rules.** `SignalEngine` adds up a score, and every rule that moves it adds a plain-language reason:

| Factor | Rule | Score |
|---|---|---|
| Trend | 20-day average above / below the 50-day average | +1 / −1 |
| Price vs average | Price above / below its 20-day average | +0.5 / −0.5 |
| RSI(14) | Below 30 (oversold) / above 70 (overbought) | +1 / −1 |
| Momentum | 20-day change above +5% / below −5% | +0.5 / −0.5 |
| Take profit (holdings only) | Up 25%+ on cost while RSI is above 65 | −1 |
| Cut losses (holdings only) | Down 15%+ on cost while the trend is down | −1 |

- **Result.** A score of 1.5 or more is BUY, −1.5 or less is SELL, and anything between is HOLD. Confidence grows with the size of the score.
- **Not enough data.** Fewer than 50 closes, or no price, gives no signal, only an explanation.

### Frontend

The `/advice` page (`trade-advice.component`) calls `getTradeAdvice` and shows each signal with its reasons and the disclaimer.

## 7. Strategy executor (automated trading)

**What it does.** The customer sets up a trading rule, and the platform places orders automatically when the rule triggers, even while the customer is logged out.

### Backend

Code is in `strategy/`.

- **Routes.** `StrategyController` at `/api/v1/accounts/{id}/strategies`: create, list, get, `PATCH` (enable or disable), delete, and `/{strategyId}/executions` for history.
- **What a strategy holds.** The symbol, the strategy type, the trade side, the quantity, and guards: `maxPosition`, `maxDailyNotional` and a maximum number of consecutive failures.
- **Strategy types:**
  - **Moving-average crossover.** Compares a short and a long simple moving average (windows between 2 and 100). The short average crossing above the long one is BUY; crossing below is SELL.
  - **Bollinger bands.** An average of the last N prices, with bands k standard deviations either side. The price dropping below the lower band is BUY; rising above the upper band is SELL. Staying outside the bands does not fire again.
  - For both types, the first evaluation only records the state, so a strategy never fires on a crossing it didn't see happen.
- **`StrategyEvaluator`** runs every 60 seconds. For each symbol with active strategies, it builds a price series from the cache's quote history and runs each strategy's indicator. A change of state in a direction the strategy trades is a trigger.
- **`StrategyExecutor`** handles each trigger in one transaction that locks the strategy row:
  1. **Disabled?** The trigger is recorded as `SKIPPED_DISABLED` and nothing is sent. Because of the lock, disabling a strategy can never race an order.
  2. **Guards.** A SELL needs the shares held. A BUY may not take the position past `maxPosition`. Today's orders may not exceed `maxDailyNotional`. A blocked trigger is recorded as `SKIPPED_NO_POSITION` or `SKIPPED_LIMIT`.
  3. **Place the order.** `StrategyTokenIssuer` mints a short-lived JWT for this one call: the owner's `accountId`, role `STRATEGY`, a one-minute expiry, and it is never stored. `TradeApiOrderClient` sends it to `POST /api/v1/orders` with the idempotency key `STRAT-{strategyId}-{quoteTime}`, so one trigger can never place two orders.
  4. **Outcome.** Success resets the failure count. A failure increments it, and at the limit the strategy turns itself off.
  5. **Audit.** Every trigger is recorded in the executions table with its indicator values and reason.
- **After placement.** The order goes through the normal pipeline (validation, the `orders` topic, the executor, `trade-events`), so the customer also gets an [order notification](#2-notifications-order-outcomes).

### Frontend

The `/strategies` page (`strategies.component` with `strategies.store.ts`) creates, enables, disables and deletes strategies, and shows each one's execution history.

## How the features connect

```
Preferences ──(channels)──► Notifications ◄── trade-events ◄── Executor ◄── orders ◄── Order API ◄── Strategy executor
     ▲                            ▲                                                                       ▲
     │                            └──────── Price alerts ◄── MarketDataCache ──► Advisor ◄── Candles      │
 Settings UI                                    ▲               │      └─────────────────────────────────┘
                                            Watchlists ─────────┘   (P&L and the charts also read the cache)
```

- **Preferences** decide where every message goes.
- **Notifications** carry order outcomes and price alerts to those channels.
- **`MarketDataCache`** feeds alerts, the advisor, strategies, P&L and the intraday chart.
- **Strategies** place real orders through the same API a customer uses, so their fills come back as ordinary notifications.
