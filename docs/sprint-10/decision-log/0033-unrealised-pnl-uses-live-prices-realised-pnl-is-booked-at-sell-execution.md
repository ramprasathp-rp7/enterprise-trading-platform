# 0033 Unrealised P&L uses live prices; realised P&L is booked at SELL execution and never recomputed

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Sahajanya |

## Context

The platform needs to display portfolio performance to users: "How much is my account worth today?" and "How much have I made or lost on this position?" These are two different questions.

**Unrealised P&L** (also called "mark-to-market") is the current profit or loss on open positions: the difference between what you paid and what you could sell for today. It changes every time prices move. Users see this in real time on the dashboard.

**Realised P&L** is the profit or loss actually locked in by selling a position. Once a SELL order executes, the P&L is fixed forever and does not change, even if the stock goes up after you sold it. This is what is recorded for tax and compliance purposes.

Calculating both accurately requires:
1. Keeping track of the average cost at which you bought each position
2. Getting the current price for each symbol (for unrealised P&L)
3. Recording the exact price and FX rate at which you sold (for realised P&L)
4. Handling multi-currency accounts: positions in GBP must be converted to the account's base currency (USD) at the live FX rate

## Options considered

| Option | For | Against |
|---|---|---|
| Compute all P&L on demand, every time a request arrives | Always up-to-date, no stored P&L figures to maintain, less storage | Requires pricing every position on every request, slow response times, expensive API calls during peak use |
| Pre-compute all P&L figures at fixed intervals (e.g., every minute) and cache them | Fast responses, users see consistent figures, reduced API load | Cache may be stale (user sees yesterday's price if server is slow), requires background jobs, storage for stale P&L records |
| Book realised P&L at SELL time (immutable), compute unrealised P&L on-demand at request time | Realised P&L is forever accurate, no recalculation risk, unrealised P&L always reflects live prices | Hybrid approach, two different code paths |

## Decision

Realised P&L is booked at the moment a SELL order reaches EXECUTED status and stored in the `pnl` column of the orders table. The formula is:

```
realised_pnl = quantity_sold * (executed_price * fill_fx_rate - average_cost_account_at_sale)
```

Once written, it is never recomputed. If you sell 100 shares at $50, the realised P&L is locked at that price forever.

Unrealised P&L is computed on-demand when the user requests portfolio data. The `PortfolioPnlService` is called for each request, which:
1. Reads all open positions for the account
2. Prices each position using the same three-tier fallback as `AccountHistoryScheduler`:
   - Market-data cache (primary, refreshed periodically)
   - Fauxnance API (fallback for cache misses)
   - Average cost (if pricing fails entirely, logged as a warning)
3. Converts each position value to the account's base currency using the live FX rate
4. Sums position values + cash balance to calculate total portfolio value
5. Returns unrealised P&L for each position and symbol, plus totals

Multi-currency handling: each position is valued in its instrument's currency, then converted to the account's base currency at the live FX rate. If the FX rate cannot be obtained, the position is excluded from totals and the response is marked as partial.

What decided it is that realised P&L must be immutable for tax and audit purposes (once you sell, your gain/loss is locked), while unrealised P&L must be live to give users accurate portfolio values. Computing unrealised on-demand keeps the implementation simple (no scheduled refresh job needed for every position) and guarantees freshness.

## Consequences

- **Realised P&L is immutable**: Once a SELL order executes, the `pnl` column is written and never changes. Rerunning the calculation, recalculating at EOD, or editing the position will not alter it. This is correct for tax purposes.
- **Unrealised P&L requires pricing at request time**: Every portfolio request triggers a pricing call (cache or Fauxnance). For large portfolios (50+ positions), this can be slow.
- **Fallback pricing is critical**: If Fauxnance is down or slow, unrealised P&L calculation falls back to average cost, which is inaccurate. The response is still returned but may be marked as degraded or partial.
- **FX rate exposure**: Multi-currency accounts depend on live FX rates. If an FX pair (e.g., GBP/USD) cannot be priced, positions in GBP are excluded from totals. The frontend must display this clearly (e.g., "portfolio value is partial, GBP prices unavailable").
- **No P&L cache table needed**: Unlike account history snapshots (which store networth once per day), there is no `portfolio_pnl` table. All unrealised P&L is transient and recalculated on demand.
- **Audit trail for realised P&L**: The order record stores the realised P&L, executed price, and FX rate at execution. This forms a complete audit trail for compliance reporting.
- **Testing complexity**: Unit tests for P&L must mock three pricing sources (cache, API, average cost) and verify the fallback chain is traversed correctly.

## Related decisions

- [[0012-valuation-reads-the-cache-first-and-calls-fauxnance-only-for-the-rest]] — valuation (used for P&L calculation) follows the same pricing fallback
- [[0008-multi-currency-placement-and-fill-rates]] — FX rates are recorded at order execution time and used for realised P&L
- [[0032-account-snapshots-are-captured-daily-at-eod-with-configurable-scheduling]] — account snapshots also price positions using the same fallback logic

## Implementation details

### Realised P&L booking (at order SELL execution)
```java
BigDecimal realisedPnl = quantitySold
    .multiply(executedPrice.multiply(fillFxRate))
    .subtract(averageCostAccountAtSale)
    .setScale(2, HALF_UP);
// Store in order.pnl, never touched again
```

### Unrealised P&L (at request time)
```java
BigDecimal unrealisedPnl = (currentPrice.multiply(fxRate) - averageCost) * quantity
// Summed across all open positions
```

### Handling missing prices
- **Cache miss + Fauxnance call fails**: Fall back to average cost, log a warning, return unrealised P&L with `isPartial=true`
- **FX rate missing**: Exclude that position from totals, mark portfolio as partial
- **No positions**: Return zeroes with `isPartial=false`

## Follow-up work

- Add a background job to periodically recompute and log unrealised P&L for audit purposes (not used for user display, only for diagnostics)
- Consider caching unrealised P&L for 1-5 minutes to reduce excessive API calls from users refreshing the dashboard frequently
- Document the FX rate fallback strategy for multi-currency deployments
