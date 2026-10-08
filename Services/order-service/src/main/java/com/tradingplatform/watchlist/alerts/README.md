# Price alerts module

A price alert tells a customer when an instrument's price crosses a level they chose. The module lives inside the watchlist package of the Trade REST API. It has its own routes, its own tables, and its own scheduled evaluator.

The contract is `Frontend/trading-ui/contracts/watchlist-api.yaml` (tag `PriceAlerts`). The UI client is generated into `src/app/api/generated/watchlist` with `npm run generate:watchlist`.

## Layout

| Package | Contents |
|---|---|
| `controller` | `PriceAlertController`, routes under `/api/v1/accounts/{accountId}/price-alerts` |
| `service` | `PriceAlertService`: create, pause/resume, delete and read, plus the caps |
| `evaluator` | `PriceAlertEvaluator` (checks quotes) and `ActiveAlertIndex` (per-quote "does anyone care?") |
| `notify` | `AlertNotifier` (the hand-off) and `InAppAlertNotifier` (the in-app channel) |
| `mapper` | `PriceAlertMapper`, `PriceAlertTriggerMapper` (MyBatis annotations) |
| `model`, `dto`, `exception` | Entities, enums, records, ALR-404 and ALR-409 |

## Routes

Every route calls `AccountAccessAuthorizer.assertTokenCanAccess(accountId)`, so a token for another account gets ACC-403.

| Method | Path | Result |
|---|---|---|
| GET | `/price-alerts?active=&symbol=` | 200, newest first |
| POST | `/price-alerts` | 201. INS-404 if the symbol is unknown, VAL-422 if the request is invalid, ALR-409 if a cap is reached or the rule is a duplicate |
| GET | `/price-alerts/triggers?since=` | 200, the account's recent triggers (max 50). The UI bell polls this |
| GET | `/price-alerts/{alertId}` | 200, or ALR-404 |
| PATCH | `/price-alerts/{alertId}` with `{ "active": false \| true }` | 200. Pauses or resumes the alert; resuming counts against the cap |
| DELETE | `/price-alerts/{alertId}` | 204. Its trigger history is deleted too |
| GET | `/price-alerts/{alertId}/triggers` | 200, the alert's firing history |

All paths are under `/api/v1/accounts/{accountId}`.

## Tables

Both tables are created by migration `016_create_price_alerts.sql`.

- `price_alerts` holds one row per alert: symbol, direction, threshold, mode, active, and the last firing.
  - `ix_price_alerts_active_symbol` is a partial index on `symbol WHERE active`, and serves the per-quote lookup.
  - `uq_price_alerts_active_rule` stops an account from holding the same active rule twice.
- `price_alert_triggers` holds one row per firing: the price, the quote time, the reason and the delivery status.
  - `uq (alert_id, quote_at)` makes firing idempotent: one quote fires a given alert at most once.

## Where quotes come from

The evaluator reads the order service's `MarketDataCache`. That is the same data `GET /api/v1/market-data/quotes/{symbol}/latest` returns, and the watchlist's live price uses it. The cache is filled from Fauxnance or from the Kafka `market-data` topic, depending on `market-data.quote-source`.

The module consumes market data only. It does not read orders or trade-events, because neither says anything about what a customer is curious about.

## Evaluation

`PriceAlertEvaluator.evaluateLatestQuotes()` runs every `price-alerts.evaluate.interval-ms` (default 30s). For each symbol:

1. **Is the quote watched and new?** `ActiveAlertIndex` keeps the set of symbols that have an active alert, and the newest quote time already evaluated for each. Unwatched symbols and quotes that haven't changed stop here, with no database call.
2. **Is the quote usable?** Stale quotes and quotes without a price are ignored.
3. **Load the alerts.** `findActiveBySymbol` returns the active alerts for that symbol, using the partial index.
4. **Did it cross?** An alert fires when both hold:
   - **ABOVE:** `price >= threshold`. **BELOW:** `price <= threshold`. Touching the level counts as crossing it.
   - The quote is newer than `last_quote_at`, the last quote that fired this alert.

## What happens when an alert fires

1. A trigger row is written as `PENDING`, with its reason, e.g. `AAPL 191.2 >= 190 (ABOVE, ONCE)`.
2. The trigger is handed to `AlertNotifier.notify(...)`. The evaluator does not choose a channel. `InAppAlertNotifier` publishes the trigger to the in-app feed by marking it `DELIVERED`; the UI bell polls `/price-alerts/triggers` and shows delivered ones. Email or push could be added as another `AlertNotifier` without changing the evaluator.
3. The alert is updated according to its mode:
   - **ONCE:** set `active = false` with `deactivated_reason = TRIGGERED_ONCE`.
   - **EVERY_QUOTE:** stays active, and fires again on every new quote past the level.

**If the notifier throws:**
- The trigger becomes `FAILED`.
- The alert is left untouched: still active, and `last_quote_at` is not advanced.
- The symbol's quote is not marked evaluated, so the next run retries the same quote. The retry updates the same trigger row instead of adding a new one.
- A failure on one alert never stops the other alerts from being processed.

## Caps

Every quote for every watched symbol is checked against the active alerts. An unbounded creation route would let one account make the evaluator itself the thing that takes the service down, so creation is capped per account:

| Property | Default | Limit |
|---|---|---|
| `price-alerts.max-active-per-account` | 20 | Active alerts, checked on create and on resume |
| `price-alerts.max-per-account` | 100 | All alerts, including paused and triggered ones |

Going over either cap gives ALR-409. The count and the insert are not locked against each other, so two simultaneous creates can exceed the cap by one.

## Configuration

| Property | Default |
|---|---|
| `price-alerts.max-active-per-account` | 20 (env `PRICE_ALERTS_MAX_ACTIVE_PER_ACCOUNT`) |
| `price-alerts.max-per-account` | 100 (env `PRICE_ALERTS_MAX_PER_ACCOUNT`) |
| `price-alerts.evaluate.interval-ms` | 30000 (env `PRICE_ALERTS_EVALUATE_INTERVAL_MS`) |
| `price-alerts.evaluate.initial-delay-ms` | 30000 |

## Tests

All are under `src/test/java/com/tradingplatform/watchlist/alerts/`.

- **`PriceAlertEvaluatorTest`** covers:
  - crossing fires, and falling short does not;
  - ONCE deactivates, and EVERY_QUOTE fires on each new quote;
  - an unwatched symbol never reaches the database;
  - delivery goes through the notifier rather than a log;
  - a notifier failure is recorded and retried.
- **`PriceAlertServiceTest`** covers account scoping, both caps, duplicate rules, and pause/resume.
- **`PriceAlertControllerTest`** checks that every route rejects another account's token.
