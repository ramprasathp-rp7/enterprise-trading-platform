# 0018 Daily candles are stored in our database, bootstrapped at startup and synced nightly

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Lavi Vasudevan |

## Context

Instrument charts and stats need daily candles. Fauxnance serves them but has a request quota, and a chart
opened by several users on one instrument would repeat identical calls for data that cannot change during the
day. The candle API contract allows ranges up to 90 days.

## Options considered

| Option | For | Against |
|---|---|---|
| Proxy Fauxnance on every chart request | Always the provider's latest | Quota burned for unchanging data; chart fails when the provider does |
| Store candles locally and serve from the database | Cheap, fast, works if the provider is down | Needs a sync job and a retention rule; data is a day behind |

## Decision

Store locally in `candle_data`. `CandleSyncScheduler` bootstraps three months for each instrument at startup
(skipped once the instrument's watermark says it completed), a cron job at 01:00 fetches the previous day, a
02:00 job retries failed syncs from `candle_sync_watermarks`, and a weekly job purges data older than 90
days. Rows are upserted on `(instrument_id, date)`, so re-running any job is safe. The deciding factor was
that end-of-day candles are fixed once published, so fetching them per request costs quota for no new
information.

## Consequences

Charts show up to the previous session only; today's movement comes from the quote cache. A failed bootstrap
leaves an instrument without history until the retry job or a restart. Retention caps history at about 90
days, so a true 52-week range is not available. Controls and limits are set by `trading.candle.*` properties.
