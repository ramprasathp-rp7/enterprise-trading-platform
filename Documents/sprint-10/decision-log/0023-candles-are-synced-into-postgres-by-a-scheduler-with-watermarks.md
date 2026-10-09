# 0013 Daily candles are copied from Fauxnance into Postgres by a scheduler, and chart requests only read Postgres

| Field | Value |
|---|---|
| Status | accepted, with a known defect in failure tracking (see Consequences) |
| Date | 2026-10-06 |
| Decided by | SarojaPandiyan (candle data story owner) |

## Context

The instrument panel draws a candlestick chart, and the trade advisor needs about 120 daily
closes per instrument. Fauxnance serves daily OHLC candles at `/candles/{symbol}?interval=1d`.
It allows 2000 requests a day, shared with the quote poller. The instrument table has grown with
`seed/007_additional_instruments.sql`.

A daily candle for a past date never changes once it is published. A customer opening a chart is
an unpredictable event, and many customers open the same instrument.

## Options considered

| Option | For | Against |
|---|---|---|
| Call Fauxnance on each chart request | No storage. Always as fresh as Fauxnance | Every chart view costs quota, and quota use grows with traffic rather than with the number of instruments. Fauxnance latency and outages land on the customer |
| Call on request and cache in memory | Fewer calls | Lost on restart. Cold-cache traffic still scales with users. Can't feed the advisor without the same calls |
| Store candles in `candle_data`. A scheduler loads the history once and then fetches one day per instrument per night. A per-instrument watermark records progress | Quota use is fixed by the number of instruments, not by traffic. Charts and the advisor read Postgres. A Fauxnance outage only delays tomorrow's candle | Today's candle is never shown, only up to yesterday. More moving parts: watermarks, retries, a purge |

## Decision

Store and sync. `CandleSyncScheduler` runs:

| When | Job | Instruments |
|---|---|---|
| On start-up (`trading.candle.bootstrap.enabled`) | Load the last 3 months, once per instrument (`bootstrap_completed`) | all |
| 01:00 daily (`trading.candle.sync.cron`) | Fetch yesterday | tradable |
| 02:00 daily (`trading.candle.sync.retry.cron`) | Retry watermarks in `FAILED` | those failed |
| 03:00 Mondays (`trading.candle.purge.cron`) | `purge_old_candle_data()` removes candles older than 90 days | all |

What decided it is quota. With stored candles, Fauxnance cost is one call per instrument per day
whoever is using the app. Per-request fetching would make the busiest day the day we run out.
Writes use `upsertBatch` on `uq_candle_data_date (instrument_id, date)`, so a re-run never
duplicates a day. `CandleChartController` (`/api/v1/instruments/{id}/candles`) only ever reads
`candle_data`.

Choices that go with it:

- **90-day window.** It matches the 3-month bootstrap, and it covers the advisor's longest
  indicator (a 50-day average over about 120 closes, which counts business days). The table
  therefore stays a fixed size.
- **Validated rows.** `ck_candle_data_prices` (high ≥ open, close, low; low ≤ open, close) and
  `volume >= 0` reject a malformed candle at the database, not in the chart.
- **`synthetic` and `adjclose` are kept** as Fauxnance sends them, so gap-filled days can be told
  apart and returns can use adjusted prices.
- **Intervals are computed, not stored.** `CandleAggregationService` groups stored daily candles
  in windows of 1, 3 or 5 (`interval=1m`, `3m`, `5m`). Only daily rows are stored.

## Consequences

- The chart ends at yesterday. The intraday quote chart covers today.
- **Defect, follow-up needed.** `bootstrapCandleData` and `syncDeltaForDate` are `@Transactional`,
  and they write the `FAILED` watermark and then rethrow a `CandleSyncException` (a
  `RuntimeException`). The rollback removes the `FAILED` row together with everything else, so
  `selectFailedSyncs()` never finds anything and the 02:00 retry never does any work. A failed
  bootstrap is still retried on the next start-up, because `bootstrap_completed` stays false. A
  failed night's delta is lost until the instrument is bootstrapped again. To fix it, write the
  watermark in a `REQUIRES_NEW` transaction or outside the transactional method.
- **Also check during that fix:** if a `FAILED` watermark did persist, the retry fetches
  `last_synced_date + 1`. A delta failure records the failed date itself as `last_synced_date`,
  so the retry would skip that day.
- The bootstrap covers every instrument, but the nightly delta only covers tradable ones. An
  untradable instrument's chart therefore stops at its bootstrap date, and the Monday purge empties
  it after 90 days.
- The interval names `1m`, `3m` and `5m` read as minutes, but they mean groups of 1, 3 or 5
  daily candles. Renaming them is a contract change for the chart client.
- Every replica of the Trade API would run the same cron jobs. That is harmless because the
  writes are upserts, but it wastes quota. Like 0010, this needs a single leader before the
  service is scaled out.
