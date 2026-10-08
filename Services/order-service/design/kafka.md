# Kafka Topics Design Decision Document

**Sprint:** 7  
**Date:** 2026-09-16  
**Status:** Binding  

## Overview

This document records the design decisions for the three Kafka topics required by the trading platform. These decisions are binding because Sprint 10 extensions consume these topics. Any renaming, field removal, or schema change breaks every downstream consumer.

## The Three Topics

### 1. `orders` Topic

**Purpose:** Work queue of orders accepted by the Trade REST API and awaiting execution.

**Rationale:**
- Orders must be decoupled from execution. The Trade REST API validates and records an order, then publishes it. The Trade Executor consumes it asynchronously.
- This separation allows the system to survive restarts, handle partial failures, and notify multiple consumers about the same order.

| Property | Value | Rationale |
|----------|-------|-----------|
| **Partitions** | 3 | Allows 3 executor instances to run concurrently in a single consumer group. This demonstrates rebalancing and shows that a consumer group cannot usefully exceed the partition count. |
| **Replication Factor** | 1 | Local development. Production would be 3. |
| **Retention** | 7 days (604,800,000 ms) | Orders are transient. Once filled, rejected, or cancelled (events on `trade-events`), the order record can be discarded. 7 days is enough to recover from a service outage without overwhelming the broker. |
| **Key** | `accountId` (string) | **Critical design decision.** Keying by account ensures all orders for the same account land on the same partition, guaranteeing per-account ordering. Two orders on one account must execute in the order they were accepted, or a sell could process before the buy that made it possible. Never key by order identifier—every message would land on its own partition, destroying the ordering guarantee. |
| **Consumer Group** | `trade-executor` (exactly one) | This is a work queue with exactly one logical consumer. Adding a second consumer group means two services executing the same order. Multiple executor instances (up to 3) belong to the same group and partition-share the work. |

**Consumer Groups:**
- `trade-executor`: The Trade Executor service. Exclusive consumer group for this topic.

### 2. `trade-events` Topic

**Purpose:** Event log of order lifecycle outcomes (filled, rejected, cancelled). The platform's source of truth for what happened to an order.

**Rationale:**
- Every interested party in the platform needs to know the outcome of an order: the account balance must be updated, customers notified, analytics loaded, and strategies told what happened.
- Publishing events decouples all these concerns. The Trade Executor publishes one event; multiple subscribers read it independently.
- A rejection is an event and must be published. Downstream consumers reporting fill rates need to know that an order failed.

| Property | Value | Rationale |
|----------|-------|-----------|
| **Partitions** | 3 | Same as `orders`: allows 3 concurrent consumers and demonstrates partition scaling limits. Per-account ordering matters for events too: a consumer must never see a fill for an account before the buy that made it possible. |
| **Replication Factor** | 1 | Local development. Production would be 3. |
| **Retention** | 30 days (2,592,000,000 ms) | Events are the audit trail. Much longer retention than orders. 30 days allows week-long outages to be recovered by replay without losing history. |
| **Key** | `accountId` (string) | Same rationale as `orders`: per-account ordering. A consumer tracking portfolio state per account needs to see events in the order they occurred for each account. |
| **Event Types** | `ORDER_FILLED`, `ORDER_REJECTED`, `ORDER_CANCELLED` | Three terminal states. An order transitions to one of these and never changes. The Trade Executor publishes `ORDER_FILLED` and `ORDER_REJECTED` (every rejection path); the Trade REST API publishes `ORDER_CANCELLED` when a customer cancels a NEW order, since cancellation happens there. |

**Cancelled orders and the executor:** the executor does not consume `trade-events`. When an `ORDER_PLACED` arrives for an order that is already `CANCELLED`, the order listener drops it before it reaches the executor: no quote, no fill or rejection, no event.

**Payload includes position state:**
- `cashDelta`, `positionQuantityAfter`, `averageCostAfter` are included so consumers can maintain their own portfolio projection without querying the trade database.
- This enables the Portfolio and P&L extension to operate without a read dependency on Postgres.

**Other payload fields:**
- `fillFxRate` (multi-currency, decision 0008): the instrument-to-account rate the executor fetched at fill, on `ORDER_FILLED`. Optional; absent or null means 1 (same currency), so events from before the change still settle correctly.
- `reason` on `ORDER_CANCELLED` is the code `CANCELLED_BY_CUSTOMER`, one of the reason codes listed in migration 007.

**Consumer Groups:**
- `trade-executor`: None (no read)
- `portfolio-service`: Maintains portfolio state from events
- `notification-service`: Notifies customers of order outcomes
- `analytics-loader`: (Optional) Python ETL pipeline
- `watchlist-service`: (Optional) Tracks price alerts
- `advice-service`: (Optional) Provides trading signals
- `strategy-service`: (Optional) Automated strategy execution

### 3. `market-data` Topic

**Purpose:** High-volume stream of quotes polled from the Fauxnance API.

**Rationale:**
- Quotes are observation events from an external data source, not side effects of our orders.
- Every strategy and watchlist service needs real-time price data.
- High volume and low value per message justify more partitions and shorter retention.

| Property | Value | Rationale |
|----------|-------|-----------|
| **Partitions** | 6 | Double the other topics to handle higher message rate. Per-symbol ordering matters: a consumer must never see an older quote for AAPL after a newer one. Keying by symbol ensures this. |
| **Replication Factor** | 1 | Local development. Production would be 3. |
| **Retention** | 1 day (86,400,000 ms) | Quotes are stale quickly. Real-time consumers process them immediately. Historical quotes are not needed; analytics pulls what it needs from the trade database. |
| **Key** | `symbol` (string) | Determines the partition, determines ordering. Within a partition, quotes for AAPL appear in time order. Quotes for different symbols can be processed in parallel. |

**Event Type:**
- `QUOTE`: Always. No other events on this topic.

**Timestamp distinction:**
- `eventTime`: When the poller published the message
- `quoteAsOf`: When Fauxnance observed the price (usually older due to quote delays)
- Consumers acting on real-time prices must use `quoteAsOf`, not `eventTime`.

**Consumer Groups:**
- `portfolio-service`: Updates market value of holdings
- `watchlist-service`: Triggers price alerts
- `advice-service`: Generates trading signals
- `strategy-service`: Executes automated strategies

---

## Rejected Alternatives and Why

### Alternative: Fewer Partitions

**Rejected:** Using 1 or 2 partitions instead of 3.

**Why rejected:**
- Demonstrates nothing about consumer group rebalancing or partition limits.
- A team writing consumers would learn nothing about scaling concerns.
- Sprint 10 extensions may need 3 executor instances; fewer partitions would bottleneck.

### Alternative: Keying by Order ID

**Rejected:** Keying `orders` and `trade-events` by order ID.

**Why rejected:**
- Every order lands on its own partition.
- Per-account ordering is lost.
- A sell can execute before the buy that made it possible, causing insufficient fund rejections.
- This is the failure mode every team hits first; explicitly rejecting it teaches why per-account keying exists.

### Alternative: Single Retention Window

**Rejected:** Using the same retention (7 days) for all topics.

**Why rejected:**
- Events are the audit trail; 7 days is too short to recover from a multi-day outage.
- `market-data` retention is driven by message volume, not audit requirements.
- Mixing concerns produces compromise configurations that optimize nothing.

### Alternative: Dead-Letter Topic Per Consumer Group

**Rejected:** Creating separate DLT topics for each consumer group.

**Why rejected:**
- A message that fails deserialisation is universally bad; multiple groups don't change that.
- A poison message should go to one DLT where it can be investigated, not scattered.
- Per-topic DLTs (not per-group) match the contract.

### Alternative: Auto-Creation

**Rejected:** Enabling auto-creation on the broker.

**Why rejected:**
- Auto-created topics get 1 partition and default retention, silently.
- This is undetectable until at runtime; wrong partition counts hide as performance issues.
- Explicit topic creation is a deliverable. Topics exist because they were deliberately configured, not by accident.

### Alternative: Transactions for Exactly-Once

**Rejected:** Using Kafka transactions for exactly-once delivery.

**Why rejected:**
- Kafka transactions ensure exactly-once between topics, but our side effects are database writes, not topic writes.
- The guarded state transition in the Trade Executor (`WHERE id = ? AND status = 'NEW'`) gives exactly-once outcome with less machinery.
- Teams should understand why they chose at-least-once over exactly-once.

---

## Dead-Letter Topics

**Naming:** `<topic>.DLT`

**Configuration:**
- Same partition count as the main topic (preserves per-account/per-symbol ordering for debugging)
- 1 day retention (enough to investigate a failure; no value keeping forever)
- Replication factor 1 (local development)

**Why this choice:**
- Matching partition counts means a poison message stays on the partition where the failure occurred, aiding investigation.
- Dead-letter topics are for rare failures, not high-volume retention.
- 1 day is enough for a team to notice and investigate; older dead-letter messages are noise.

---

## Serialisation and Envelope

**Format:** JSON with UTF-8 encoding

**Envelope (5 required fields + payload):**

| Field | Type | Purpose |
|-------|------|---------|
| `eventId` | UUID string | Unique per message. Idempotency key for consumers. |
| `eventType` | Enum string | Discriminates the payload. Allows one topic to carry multiple event types. |
| `eventTime` | RFC 3339 UTC | When the producer created the event, not when it was consumed. |
| `source` | String | Producing component: `trade-api`, `trade-executor`, `market-poller`. |
| `schemaVersion` | Integer | Starts at 1. Only incremented on breaking changes. |
| `payload` | Object | Topic-specific and event-type-specific data. |

**Consumer contract:**
- Consumers MUST ignore fields they do not recognise.
- Adding optional fields is not a breaking change and does not increment `schemaVersion`.
- Removing, renaming, or retyping a field is breaking and must increment `schemaVersion`.

---

## Producer and Consumer Matrix

| Service | `orders` | `trade-events` | `market-data` |
|---------|----------|-----------------|---------------|
| Trade REST API | **produce** | **produce** (`ORDER_CANCELLED` only); consume (group `order-notifications`) | consume (quote cache, `KAFKA` mode) |
| Trade Executor | **consume** | **produce** | produce (market poller) |
| Python ETL | not used | consume (optional, group `analytics-loader`) | not used |
| Portfolio Service (Trade REST API) | not used | consume | consume |
| Watchlist Service (Trade REST API) | not used | not used | consume |
| Notification Service (Trade REST API) | not used | consume (group `order-notifications`: stores order outcomes for the UI bell) | not used |
| Strategy Service (Trade REST API) | not used | consume | consume |
| Advice Service (Trade REST API) | not used | consume | consume |
| Angular UI | ❌ never | ❌ never | ❌ never |

**Rules:**
1. `orders` has exactly one consumer group (`trade-executor`). It is a work queue. If something else needs to know an order was placed, it reads `trade-events` or the API publishes a separate event.
2. Multiple consumer instances sharing a group ID will partition-share the work and see only their part of the stream. Every consumer MUST set an explicit `group.id`. Reusing group IDs between different services is a silent failure mode (messages missing at random).
3. Group IDs are per logical service, not per process. Even if modules run in one container, separate group IDs keep each module's offsets independent and isolate redeployments.

---

## Delivery Semantics: At-Least-Once

The platform operates at-least-once delivery. Producers retry, consumers commit offsets after processing, and duplicates happen.

### Producer Configuration

Set these in every producer:
- `acks=all`: Wait for all in-sync replicas to acknowledge. (Local development will have 1; production will have 3.)
- `enable.idempotence=true`: Idempotent producers remove duplicates caused by a producer retry.
- `retries` high: Retry transient failures.
- `max.in.flight.requests.per.connection=5` or fewer: Bounds reordering on failure + retry.

**What idempotence does not fix:**
- Application-level retries after a crash will produce duplicates. If the Trade REST API publishes an order and crashes before recording success, a retry will publish again.

### Consumer Configuration

Every consumer:
- **Disable auto-commit.** Explicitly commit offsets only after processing succeeds.
- **Commit offset AFTER processing, never before.** Committing first means a crash loses the message. Committing after means a crash reprocesses it. Reprocessing is survivable if idempotent; losing a trade is not.
- **Set an explicit `group.id`.** Do not rely on defaults.

### Idempotent Handling

Every consumer with a side effect must handle the same `eventId` twice without doing the work twice. Two mechanisms are acceptable:

**Mechanism 1: Processed Events Table**
```sql
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  event_time TIMESTAMP,
  service_name VARCHAR(100)
);

-- In the consumer:
BEGIN;
INSERT INTO processed_events (event_id, ...) VALUES (...);
-- Do side effect here (e.g., update account balance)
COMMIT;
-- On duplicate: PK violation, rollback, nothing happens
```

**Mechanism 2: Guarded State Transition (Preferred)**
```sql
UPDATE orders SET status = 'FILLED', ... 
WHERE id = ? AND status = 'NEW';

-- Exactly 0 or 1 rows affected. 
-- 1 = first time, did the work
-- 0 = already handled, do nothing
```

The Trade Executor uses **Mechanism 2**. It is the exact mechanism demonstrated in the Sprint 7 acceptance check: replay a consumed `ORDER_PLACED` message and show that the cash balance does not move twice.

### Dead-Letter Handling

A message that fails goes to a dead-letter topic:
1. **Malformed message** (e.g., invalid JSON): Send to DLT immediately. It will never succeed.
2. **Transient failure** (e.g., Fauxnance returning 503): Retry with exponential backoff. Dead-letter only after the retry budget is exhausted.

A poison message must not block a partition indefinitely. One bad message on a partition stops every account keyed to that partition.

**Dead-letter headers:**
- Original message is the value
- Failure reason in a header
- Original topic and partition in headers for tracing

### Ordering Guarantee

- **Within a partition:** Guaranteed. Messages arrive in the order they were published.
- **Across partitions:** Not guaranteed. A consumer that assumes it will see a fill for account 1 before a fill for account 2 is relying on an ordering the platform never promised.
- **Per account and per symbol:** Guaranteed, because `orders`, `trade-events`, and `market-data` are keyed by account or symbol.

### Exactly-Once

Kafka transactions can give exactly-once between topics. This platform does not use them.

**Why not?**
- Our side effects are database writes, not topic writes.
- The guarded transition (`WHERE status = 'NEW'`) gives exactly-once outcome without the overhead.
- A team must explain this choice: you chose at-least-once processing with idempotent handlers over exactly-once delivery.

---

## Security in Production

**Local development:** Plaintext, no authentication.

**Production (not implemented, documented only):**
- **TLS:** Encrypt client-broker communication. Certificate pinning recommended.
- **SASL:** Client authentication. PLAIN (for ease), SCRAM-SHA-256 (better), or OAUTHBEARER (for cloud).
- **ACLs:** Per-topic access control.
  - Only Trade REST API can write to `orders`
  - Only Trade Executor can read `orders`
  - Only Trade Executor can write `market-data`; Trade Executor (fills, rejections) and Trade REST API (cancellations) can write `trade-events`
  - Portfolio Service reads `trade-events` and `market-data`
  - Etc.

**Never in payloads:**
- Credentials, API keys, tokens
- Full names, email addresses, PII
- Topics are retained for days and read by services that have no need for this data.

---

## Running Locally

**Setup script:** `scripts/kafka-setup.sh`

**Usage:**
```bash
# Create topics (idempotent, safe to run multiple times)
./scripts/kafka-setup.sh

# With custom broker
./scripts/kafka-setup.sh --bootstrap-server kafka:9092

# Clean and recreate (e.g., after resetting the broker volume)
./scripts/kafka-setup.sh --cleanup
```

**Verification:**
```bash
# List all topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe one topic
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders

# Watch consumer lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group trade-executor --describe
```

**Monitor:**
- Watch consumer lag on `market-data`. If lag climbs steadily, the poller interval is shorter than the consumer's processing time.
- Watch for poison messages in DLT topics. One bad message blocks a partition.

---

## Acceptance Criteria

At Sprint 7 review, verify:

1. ✅ **The three topics exist** with the contracted names, partition counts, replication factor, and retention:
   - `orders`: 3 partitions, 7-day retention
   - `trade-events`: 3 partitions, 30-day retention
   - `market-data`: 6 partitions, 1-day retention

2. ✅ **Dead-letter topics exist** alongside them:
   - `orders.DLT`, `trade-events.DLT`, `market-data.DLT`
   - Same partition count as main topic, 1-day retention

3. ✅ **One command creates the entire setup**, runs without prompting, and is idempotent:
   - `scripts/kafka-setup.sh`
   - Can be run against a fresh broker or an existing broker
   - Resetting the broker volume and re-running the script recreates all topics in the correct shape

4. ✅ **Auto-creation is off** on the broker. Verify:
   ```bash
   kafka-configs.sh --bootstrap-server localhost:9092 --entity-type brokers --entity-name 0 --describe | grep auto.create
   # Should show: auto.create.topics.enable=false
   ```

5. ✅ **Design decisions are recorded here** in `design/kafka.md`:
   - Why each partition count was chosen
   - Why keys were chosen as they were
   - What dead-letter configuration was chosen and why
   - What alternatives were rejected and why

6. ✅ **Production plan is documented here**:
   - TLS, SASL, ACLs section above
   - Not claimed as implemented; merely planned

7. ✅ **Idempotent consumer handling is demonstrated**:
   - Trade Executor replays an `ORDER_PLACED` message
   - Show that the cash balance does not move twice
   - This is the signature of a team that designed for at-least-once delivery

---

## Appendix: Example Commands

### Create Topics Manually (not recommended; use the script)

```bash
# orders
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic orders --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

# trade-events
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic trade-events --partitions 3 --replication-factor 1 \
  --config retention.ms=2592000000

# market-data
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic market-data --partitions 6 --replication-factor 1 \
  --config retention.ms=86400000

# Dead-letter topics
for topic in orders trade-events market-data; do
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic "${topic}.DLT" --partitions $([ "$topic" = "market-data" ] && echo 6 || echo 3) \
    --replication-factor 1 \
    --config retention.ms=86400000
done
```

### Describe All Topics

```bash
kafka-topics.sh --bootstrap-server localhost:9092 --describe
```

### Delete a Topic

```bash
kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic orders
```

### Monitor Consumer Lag

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group trade-executor --describe
```

### Reset Broker (delete all topics)

```bash
# Bring down Kafka and Zookeeper
# Remove the data volumes: docker volume rm kafka_data zookeeper_data
# Bring up Kafka and Zookeeper
# Run: ./scripts/kafka-setup.sh
```

---

**Document Owner:** Platform Team  
**Last Updated:** 2026-09-16  
**Review Cycle:** Per sprint  

