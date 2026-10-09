# 0022 The Today chart re-reads the quote cache every minute and follows the headline quote

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Lavi Vasudevan |

## Context

The quotes chart plots the day's rows from `GET /api/v1/market-data/quotes`. The backend refreshes its cache
from Fauxnance every five minutes on its own clock, and the headline price polls `/quotes/{symbol}/latest` every
five minutes from when the panel opened. With a five-minute poll on a separate clock, the chart could trail
the headline by up to five minutes and take about ten to show a new quote; users had to switch ranges to force
a refetch.

## Options considered

| Option | For | Against |
|---|---|---|
| Poll every five minutes, matching the backend | Fewest requests | Drifts against the backend refresh; visible lag |
| Poll every minute | New rows appear within about a minute | More requests (cache reads only, no Fauxnance quota) |
| Poll every minute and refetch when the headline quote changes | Chart and headline agree | Slightly more logic |

## Decision

Poll every minute while Today is selected, refetch as soon as the headline quote's `receivedAt` changes, skip
ticks while the browser tab is hidden and catch up when it is shown. If the chart finds a row newer than the
headline, the headline is refetched too. If nothing changed the same data object is kept so the hover tooltip
is not redrawn. The requests only read the order service's cache, so the extra polling costs no provider quota.

## Consequences

Up to one extra request per minute per open Today chart. A failed refresh keeps the points already drawn. The
chart is only as fresh as the backend's five-minute refresh.
