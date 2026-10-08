# 0032 Account snapshots are captured daily at end-of-day with configurable scheduling

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Sahajanya |

## Context

The platform needs historical records of account state (cash balance, positions, and networth) for compliance reporting, performance attribution, and portfolio analytics. Previously, there was no way to retrieve "what was the account worth on a specific date?" or to track how an account's value changed over time.

The business requirement is to capture a snapshot of each active account once per day at end-of-day (after market close), not in real time. The snapshot includes: cash balance, all positions, their current prices, and calculated networth (cash + sum of position values).

Pricing snapshots requires accessing multiple data sources: the market-data cache (primary, near-real-time), Fauxnance API (fallback), or the average cost basis of the position itself (last resort).

## Options considered

| Option | For | Against |
|---|---|---|
| Capture snapshots on-demand via a manual API call only | No scheduler needed, simpler code | Requires ops to remember to run it daily, gaps in history if forgotten, no audit trail of when snapshots were taken |
| Scheduled job that runs daily at a fixed time (23:59 UTC) | Consistent, automatic, historical completeness, auditable | Scheduler complexity, time zone handling, accounts in different regions may want different snapshot times |
| Event-driven: capture on every order/position change | Real-time completeness | Excessive writes (thousands per day per account), difficult to know which snapshot is "the" daily one, expensive storage |

## Decision

A `@Scheduled` Spring task runs daily at 23:59 UTC (configurable via `scheduler.account-history.cron` in `application.properties`). The job can be disabled with `scheduler.account-history.enabled=false` if needed, but manual snapshots can still be triggered via `POST /api/v1/admin/account-history/snapshot` (admin only).

The `AccountHistoryScheduler` iterates over all active accounts and for each one:
1. Reads cash_balance from the accounts table
2. Reads all positions for that account
3. Prices each position using a three-tier fallback:
   - Market-data cache (primary, refreshed by MarketDataRefreshScheduler)
   - Fauxnance API call (if symbol not in cache)
   - Average cost basis (if API call fails, logged as a warning)
4. Calculates networth = cash_balance + sum(quantity × price)
5. Upserts into the account_history table on (account_id, snapshot_date)

What decided it is that a scheduled job is the simplest way to meet the requirement of daily completeness and auditability without requiring manual intervention, while still allowing ad-hoc snapshots when needed (e.g., for debugging).

## Consequences

- **Automatic daily history**: Each morning, every active account has a new row in the account_history table stamped with yesterday's 23:59:59 UTC. No manual steps required.
- **Non-blocking per-account**: If one account's snapshot fails (e.g., a symbol cannot be priced), it is logged and skipped. Other accounts' snapshots complete normally. This prevents one bad account from breaking the entire job.
- **Upserts are safe to re-run**: Snapshots for the same day and account are upserted (INSERT ... ON CONFLICT), so running the scheduler twice on the same day overwrites the earlier snapshot. No duplicates accumulate.
- **Pricing fallback strategy**: Accounts with positions that have no live price in Fauxnance fall back to average cost. This means the networth figure may lag slightly behind reality for illiquid or delisted symbols, but the snapshot still completes.
- **Manual trigger via admin API**: The same logic can be run on demand via the admin API, allowing ops to capture a snapshot at any time (e.g., for testing or immediate reporting).
- **Configurable cron and time zone**: The cron expression and time zone are in `application.properties`, so regional variations (e.g., capture at 17:00 ET instead of 23:59 UTC) can be implemented without code changes.
- **Performance**: For an account with 50 positions, the scheduler must price each one. Caching market data and avoiding redundant API calls are critical. If pricing is slow, the end-of-day window (23:59 to midnight) may be too tight.

## Related decisions

- [[0023-candles-are-synced-into-postgres-by-a-scheduler-with-watermarks]] — same scheduling pattern used for market data syncs
- [[0012-valuation-reads-the-cache-first-and-calls-fauxnance-only-for-the-rest]] — same pricing fallback strategy

## Follow-up work

- Add monitoring and alerting for scheduler failures (e.g., if no snapshots complete on a given day)
- Consider parallel processing of accounts if the end-of-day window becomes a bottleneck
- Document the timezone assumption and regional deployment implications
