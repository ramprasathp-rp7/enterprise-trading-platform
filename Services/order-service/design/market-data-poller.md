# Market Data Poller: Design & Quota Arithmetic

## Overview

The Market Data Poller is a scheduled component inside the Trade Executor that polls the Fauxnance API at regular intervals to fetch quotes for symbols with active positions, and publishes them to the Kafka `market-data` topic.

The poller is designed to stay within the 2000-requests-per-day quota shared with the Trade Executor's order-fill path. The interval and quota are carefully calculated to ensure the poller does not starve the fill path of API requests.

## Architecture

**Location**: `com.tradingplatform.poller.MarketDataPoller` (Spring `@Component`, scheduled)

**Lifecycle**:
1. Initializes at application startup
2. Runs on a fixed interval (configurable via `POLL_INTERVAL_SECONDS` env var)
3. Enforces minimum 60-second interval in code
4. Gracefully handles errors (logs but does not crash)
5. Continues indefinitely until application shutdown

**Scheduling**: Uses Spring `@Scheduled` with fixed delay, computed from `POLL_INTERVAL_SECONDS`.

## Quota Budget

### Daily Limit: 2000 Requests

The Fauxnance API allows 2000 requests per day, resetting at 00:00 UTC.

This budget is **shared** between:
- **Market Data Poller**: Fetches quotes for symbols with active positions
- **Order Fill Path**: Fetches a single quote to price each order fill

### Request Accounting

Each HTTP request to Fauxnance counts as **one** request against the quota, regardless of how many symbols are included:
- `GET /quotes?symbols=AAPL` = 1 request
- `GET /quotes?symbols=AAPL,MSFT,GOOGL` = 1 request (batched)
- `GET /quotes?symbols=AAPL,MSFT,...` (up to 25 symbols) = 1 request

The poller batches up to 25 symbols per request, so the request count depends on how many distinct symbols are held:
- 8 symbols → 1 request per poll
- 25 symbols → 1 request per poll
- 50 symbols → 2 requests per poll
- 100 symbols → 4 requests per poll

### Quota Arithmetic Examples

#### Example 1: Conservative (Safe)
- **Symbols held**: 8
- **Poll interval**: 60 seconds
- **Requests per day**: `(86400 / 60) × 1 = 1440` requests/day
- **Poller quota**: 1440 requests
- **Remaining for fills**: `2000 - 1440 = 560` requests
- **Status**: ✅ Safe. Enough buffer for fills.

#### Example 2: Aggressive (Risky)
- **Symbols held**: 8
- **Poll interval**: 30 seconds
- **Requests per day**: `(86400 / 30) × 1 = 2880` requests/day
- **Poller quota**: 2880 requests
- **Remaining for fills**: `2000 - 2880 = -880` (EXCEEDS LIMIT!)
- **Status**: ❌ Unsafe. Quota exhausted in ~16.6 hours.

#### Example 3: Balanced
- **Symbols held**: 8
- **Poll interval**: 120 seconds
- **Requests per day**: `(86400 / 120) × 1 = 720` requests/day
- **Poller quota**: 720 requests
- **Remaining for fills**: `2000 - 720 = 1280` requests
- **Status**: ✅ Safe. Large buffer for fills.

#### Example 4: At Scale
- **Symbols held**: 50
- **Poll interval**: 60 seconds
- **Requests per day**: `(86400 / 60) × 2 = 2880` requests/day (since 50 symbols = 2 batches)
- **Poller quota**: 2880 requests
- **Status**: ❌ Unsafe. Exceeds limit.
- **Solution**: Use longer interval (e.g., 120s) → 1440 requests/day → ✅ Safe

## Environment Variables

### POLL_INTERVAL_SECONDS
- **Description**: Interval (in seconds) at which the poller runs
- **Type**: Integer
- **Default**: 300 (every five minutes; 288 requests/day for up to 25 symbols)
- **Minimum**: 60 (enforced in code with WARNING log)
- **Source**: OS environment variable or Spring property
- **Example**: `export POLL_INTERVAL_SECONDS=120`

### FAUXNANCE_API_KEY
- **Description**: API key for Fauxnance (shared with fill path)
- **Type**: String
- **Source**: OS environment variable
- **Security**: Never logged or committed
- **Example**: `export FAUXNANCE_API_KEY=sk_live_abc123...`

### KAFKA_BOOTSTRAP_SERVERS
- **Description**: Kafka broker address
- **Type**: String
- **Default**: `localhost:9092`
- **Source**: OS environment variable or Spring property
- **Example**: `export KAFKA_BOOTSTRAP_SERVERS=kafka:29092`

## Quota Monitoring

### Local Counter (Automatic)

The `FauxnanceQuoteClient` maintains a thread-safe counter of daily requests:

- **Location**: `AtomicInteger requestCountToday` in `FauxnanceQuoteClient`
- **Reset**: Automatic at UTC midnight (thread-safe date check)
- **Access**: `quoteClient.getTodayRequestCount()` and `quoteClient.getRemainingQuotaBudget()`

### Poller Logging

The poller logs quota status after each poll cycle:

```
INFO: Fauxnance quota status: 250 used, 1750 remaining out of 2000/day
WARN: Approaching Fauxnance quota limit: only 50 requests remaining for today!
```

**When to Investigate**:
- Log level shows `WARN` → Less than 100 requests remaining
- Quota is approaching exhaustion faster than expected → Check if fill rate has spiked
- Quota is exhausted before end of day → Reduce poll interval or contact team

### Manual Verification

To verify quota counter accuracy (without consuming additional requests):

1. Check application logs for most recent quota status:
   ```bash
   grep "Fauxnance quota status" logs/application.log | tail -1
   ```

2. Extract used/remaining from logs:
   ```
   Fauxnance quota status: 1200 used, 800 remaining out of 2000/day
   ```

3. No external API call needed — all tracking is local to the application.

## Failure Modes

### Poller Exhausts Quota

**Symptom**: Fill path starts rejecting orders with "Pricing unavailable" errors.

**Cause**: Poller consumed the entire 2000-request budget before end of day.

**Resolution**:
1. Increase poll interval (e.g., 60s → 120s) to reduce poller request rate
2. Or, reduce symbols being polled (e.g., remove inactive watchlist symbols)
3. Monitor application logs for quota status to prevent recurrence
4. Check if fill volume has increased unexpectedly

### Poller Stops Running

**Symptom**: No new messages appear on `market-data` topic for extended period.

**Cause**: Poller crashed, or Spring scheduling stopped, or application restarted.

**Resolution**:
1. Check application logs for exceptions in `MarketDataPoller`
2. Verify Spring scheduling is enabled (`@EnableScheduling` is active)
3. Verify `SymbolDiscoveryService` is working (check for position mapping errors)
4. Restart application if necessary

### Empty Symbol Set

**Symptom**: Poller runs but publishes no messages.

**Cause**: No active positions (account has zero holdings).

**Expected Behavior**: 
- Poller checks for active symbols
- Finds none (empty set)
- Skips polling for that cycle
- Continues on next interval
- This is normal and not an error

### Database Connection Error

**Symptom**: `SymbolDiscoveryService` returns empty set despite active positions.

**Cause**: Database is unreachable or slow.

**Resolution**:
1. Check database connectivity
2. Verify Postgres is running and accessible
3. Check application logs for SQL errors
4. The poller will resume once database is available again

## Deployment Checklist

- [ ] Set `POLL_INTERVAL_SECONDS` ≥ 60 in environment
- [ ] Calculate quota arithmetic for your expected symbol count:
  - `requests_per_day = (86400 / POLL_INTERVAL_SECONDS) * (symbols / 25)`
  - Verify: `requests_per_day + fill_rate < 2000`
- [ ] Ensure `KAFKA_BOOTSTRAP_SERVERS` points to running broker
- [ ] Ensure `FAUXNANCE_API_KEY` is set and valid
- [ ] Verify Postgres is running and accessible
- [ ] Run unit tests: `mvn clean test -Dtest=MarketDataPollerTest`
- [ ] Monitor logs after first deployment for quota status
- [ ] Alert threshold: If remaining < 100, investigate and adjust interval

## Design Decisions

### Why Batch Up to 25 Symbols?

Fauxnance's batch endpoint accepts up to 25 symbols in a single request. Batching reduces:
- Total request count (8 symbols in 1 request vs. 8 separate requests)
- Latency (1 round-trip vs. 8 round-trips)
- Overall API pressure

### Why One Message Per Symbol (Not Per Batch)?

The contract requires per-symbol ordering: newer quotes for the same symbol must never appear after older ones. Publishing one batch message containing multiple symbols would violate this:
- Batch: `{symbols: [AAPL, MSFT, GOOGL], prices: [...]}`
- Consumer reading this message would process all three at once
- Per-symbol ordering would be lost

Instead, we publish one message per symbol:
- Message 1: `{symbol: AAPL, price: ...}` → keyed by AAPL
- Message 2: `{symbol: MSFT, price: ...}` → keyed by MSFT
- Message 3: `{symbol: GOOGL, price: ...}` → keyed by GOOGL
- Kafka guarantees ordering per key (per symbol)

### Why 60-Second Minimum Interval?

- At 60s: `86400 / 60 = 1440` requests/day for the poller
- At 30s: `86400 / 30 = 2880` requests/day (quota exhausted in ~16.6 hours)
- At 15s: `86400 / 15 = 5760` requests/day (quota exhausted in ~5 hours)

The 60-second floor ensures the poller alone never exhausts the full budget, leaving space for order fills. Any shorter interval would be incompatible with a production trading system.

### Why Track Quota Locally (Not Via /usage Endpoint)?

Calling Fauxnance's `/usage` endpoint would itself consume one request per check. Checking every poll cycle would add 1440 extra requests/day just to check the quota. Instead, we track locally:
- No extra API calls
- Zero overhead
- Accurate (as long as only this application uses the key)

## Monitoring & Observability

### Logs to Watch

**Normal Operation**:
```
INFO: Discovering {} active symbols
INFO: Polling {} symbols for market data
INFO: Published quote to market-data: symbol=AAPL
INFO: Fauxnance quota status: 250 used, 1750 remaining out of 2000/day
```

**Warnings**:
```
WARN: Configured poll interval 30s is below minimum 60s; enforcing minimum
WARN: Approaching Fauxnance quota limit: only 50 requests remaining for today!
WARN: Failed to fetch quotes for 8 symbols: Connection refused
```

**Errors** (usually recoverable):
```
ERROR: Unexpected error in market data poller: NullPointerException
ERROR: Failed to publish quote for symbol AAPL: Kafka timeout
```

### Metrics to Track

1. **Poller Cycles**: Count of poll executions per day
2. **Symbols Polled**: Average number of symbols discovered
3. **Quotes Published**: Count of messages sent to market-data topic
4. **Fauxnance Requests**: Daily request count from quota counter
5. **Remaining Quota**: Daily remaining requests
6. **Failed Publishes**: Count of messages that failed to publish to Kafka

### Alert Thresholds

- 🟢 **Green**: Remaining quota > 500
- 🟡 **Yellow**: Remaining quota 100–500 (adjust interval soon)
- 🔴 **Red**: Remaining quota < 100 (urgent: adjust interval or reduce symbols)

## Future Enhancements

1. **Watchlist Support**: Extend `SymbolDiscoveryService` to include symbols from a watchlist table
2. **Dynamic Interval**: Allow changing poll interval at runtime without redeployment
3. **Metrics Export**: Publish quota and performance metrics to a monitoring system
4. **Selective Polling**: Skip symbols that haven't traded in last N hours
5. **Quote Caching**: Publish only quotes that changed significantly since last poll

## References

- Kafka Topics Contract: `kafka-topics.md`
- Sprint 7 Specification: `SPEC_README.md`
- Trade Executor Design: `classDiagram.mmd`, `sequenceDiagram.mmd`
- Fauxnance API Documentation: Inline in `FauxnanceQuoteClient.java`
