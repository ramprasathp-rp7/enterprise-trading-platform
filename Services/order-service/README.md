# Order Service (Trade REST API)

The platform's main backend. It takes orders, holds accounts, cash, positions and the transaction ledger, and
serves everything the Angular app shows except sign-in. Spring Boot 3.4, Java 21, MyBatis on PostgreSQL, Kafka.

Port: **8081** (from `Config/application.yaml`, `services.order.port`).

## What it contains

| Package (`src/main/java/com/tradingplatform/…`) | What it does |
|---|---|
| `api.controllers`, `api.services` | Orders, accounts, balances, positions, transactions, portfolio P&L, fees, health, market data and candles |
| `api.events`, `api.config` | Kafka message envelopes and producers (`orders`, `trade-events`) |
| `api.security` | JWT resource server; every account route checks the token's account (`ACC-403`) |
| `api.exception` | `GlobalExceptionHandler`: domain errors become catalogue codes (`ORD-409`, `VAL-422`, …) |
| `instruments` | Instrument list and symbol discovery through the Fauxnance registry |
| `watchlist`, `watchlist.alerts` | Watchlists, and price alerts evaluated against the quote cache |
| `notifications` | Order and price-alert notifications, notification history, email/SMS sender stubs |
| `preferences` | Alert channels per customer and admin (contact details encrypted) |
| `settings` | Trading settings per account: order budget, market, default quantity |
| `strategy` | Automated strategies (moving-average crossover, Bollinger bands) that order through the API |
| `advice` | Rule-based buy/sell/hold signals |
| `domain` | Entities, enums, DTOs and domain exceptions |

Kafka:

| Topic | Direction | Purpose |
|---|---|---|
| `orders` | publishes | `ORDER_PLACED`, picked up by the executor |
| `trade-events` | publishes | `ORDER_CANCELLED` when a customer cancels a NEW order |
| `trade-events` | consumes | `ORDER_FILLED` / `ORDER_REJECTED` (and cancellations): fee deduction and notifications |
| `market-data` | consumes | Quotes, when `MARKET_DATA_QUOTE_SOURCE=KAFKA` |

Design notes are in [`design/`](design/) (class and sequence diagrams, Kafka and poller notes) and the
decision log in [`Documents/sprint-10/decision-log/`](../../Documents/sprint-10/decision-log/).

## Setup

### Prerequisites

- JDK 21 and Maven 3.9+
- PostgreSQL 15+ with the `trading_system_db` database
- Kafka (the platform's broker, host from `EC2_PRIVATE_IP`, port from `Config/application.yaml`, default 9092)
- A `.env` at the repository root (copy `.env.example`); this service needs at least `PGPASSWORD`,
  `FAUXNANCE_API_KEY`, `FAUXNANCE_BASE_URL`, `JWT_SECRET`, `JWT_ISSUER` and `PREFERENCES_CONTACT_KEY`

### Database

Apply the migrations and seed data (from the repository root, Git Bash or WSL):

```bash
bash Database/postgres/order-service/apply.sh
```

It reads `POSTGRES_DB` and the `PG*` settings from `.env`, runs `migrations/` in order, then `seed/`.

### Run

```bash
cd Services/order-service
mvn spring-boot:run
```

The service reads `../../.env` and `../../Config/application.yaml` relative to the directory you start
it from, so start it from `Services/order-service`. Any setting can be overridden with an environment
variable, for example `SERVICES_ORDER_PORT=8090` or `DATABASE_NAME=other_db`.

Check it is up: `GET http://localhost:8081/health`.

### Useful settings

| Variable | Default | Effect |
|---|---|---|
| `TRADING_EXECUTION_MODE` | `ASYNC` | `ASYNC` publishes orders to Kafka; `SYNC` settles them in the request |
| `MARKET_DATA_QUOTE_SOURCE` | `FAUXNANCE` | Where the quote cache comes from: Fauxnance every 5 minutes, or the `market-data` topic |
| `MARKET_DATA_CACHE_WIPE_ON_STARTUP` | `false` | Start with an empty quote cache |
| `TRADING_STRATEGY_ENABLED` | `true` | Run the strategy evaluator |
| `PRICE_ALERTS_MAX_ACTIVE_PER_ACCOUNT` | `20` | Active price alerts per account |
| `SCHEDULER_ACCOUNT_HISTORY_ENABLED` | `true` | Nightly account snapshot at 23:59 UTC |

## Tests

```bash
mvn test                 # unit and MockMvc tests, plus the JaCoCo report
```

The coverage report is written to `target/site/jacoco/index.html`. See
[`Documents/003_notes_on_testing_and_coverage.md`](../../Documents/003_notes_on_testing_and_coverage.md)
for every service's test and coverage commands.
