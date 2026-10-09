# Executor Service (Trade Executor)

Fills or rejects the orders the order service accepts. It consumes `ORDER_PLACED` from Kafka, fetches a live
quote, applies the fill rule, settles cash and positions in PostgreSQL, and publishes the outcome. It also polls
Fauxnance for every instrument and publishes the quotes. Spring Boot 3.2, Java 21, MyBatis, Kafka.

Port: **8082** (from `Config/application.yaml`, `services.executor.port`).

## What it contains

| Package (`src/main/java/com/tradingplatform/…`) | What it does |
|---|---|
| `kafka` | `OrderPlacedListener`: consumes `orders`, sends records it cannot process to `orders.DLT` |
| `executor` | `OrderExecutorService`: locks the order row, settles it once, records the result |
| `fill` | `DefaultFillRule`: a BUY fills at or below its limit against the ask, a SELL at or above against the bid |
| `services` | Settlement (cash, positions, realised P&L at the fill FX rate) and symbol discovery for the poller |
| `quote` | Fauxnance quote client, FX rates and the shared daily request quota |
| `publisher` | Publishes `ORDER_FILLED` / `ORDER_REJECTED` to `trade-events`; a replay job resends any that never reached Kafka |
| `poller` | `MarketDataPoller`: every 5 minutes, publishes one `QUOTE` per instrument to `market-data` |
| `domain`, `enums`, `events`, `mappers` | Entities, message envelopes and MyBatis mappers |

Kafka:

| Topic | Direction | Purpose |
|---|---|---|
| `orders` | consumes | `ORDER_PLACED` from the order service |
| `orders.DLT` | publishes | Records that could not be processed |
| `trade-events` | publishes | `ORDER_FILLED` / `ORDER_REJECTED` |
| `market-data` | publishes | Quotes for every instrument |

How a replayed message is kept from settling an order twice is described in
[`Documents/001_notes_on_double_debit_replay_verification.md`](../../Documents/001_notes_on_double_debit_replay_verification.md),
and the SonarQube setup in [`Documents/002_notes_on_SonarQube_quality_gate.md`](../../Documents/002_notes_on_SonarQube_quality_gate.md).

## Setup

### Prerequisites

- JDK 21 and Maven 3.9+
- PostgreSQL 15+ with the order-service schema applied (`bash Database/postgres/order-service/apply.sh`);
  the executor uses the same database and creates no tables of its own
- Kafka (host from `EC2_PRIVATE_IP`, port from `Config/application.yaml`, default 9092)
- A `.env` at the repository root with `PGPASSWORD`, `FAUXNANCE_API_KEY` and `FAUXNANCE_BASE_URL`

### Run

```bash
cd Services/executor-service
mvn spring-boot:run
```

Start it from `Services/executor-service`: it reads `../../.env` and `../../Config/application.yaml`
relative to that directory. Settings can be overridden with environment variables, e.g.
`SERVICES_EXECUTOR_PORT`, `POLL_INTERVAL_SECONDS` (default 300, never below `MIN_POLL_INTERVAL_SECONDS`, 60).

Orders placed through the order service (`TRADING_EXECUTION_MODE=ASYNC`, the default) move from NEW to
FILLED or REJECTED within a few seconds once both services and Kafka are running.

## Tests

```bash
mvn test       # unit tests and the JaCoCo report
mvn verify     # the same, plus the packaging checks
```

The coverage report is written to `target/site/jacoco/index.html`. See
[`Documents/003_notes_on_testing_and_coverage.md`](../../Documents/003_notes_on_testing_and_coverage.md).
