# 0011 A firing is recorded before the notifier is called, and a failed hand-off keeps the alert live and retries the same quote

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | SarojaPandiyan (price alerts story owner) |

## Context

When an alert crosses its level, three things must happen: the customer is told, the firing is
kept as history, and the alert's state moves on (a `ONCE` alert switches off). Telling the customer
was in-app only when this was built. Email and SMS were known to be coming once the preferences
module (0001–0007) was ready, and those calls can fail.

The question is what happens when the hand-off fails. A `ONCE` alert that switches off without the
customer hearing about it is the worst outcome, because nothing shows it ever happened.

## Options considered

| Option | For | Against |
|---|---|---|
| Notify first, then record the trigger and update the alert | Simple | A crash between the two loses the history. A failed notify leaves no record that the alert fired at all |
| Record the trigger, update the alert and notify in one transaction | All or nothing | The notifier is an outside call. A rollback after a successful send means the customer was told but nothing records it, and the retry tells them again |
| Write the trigger as `PENDING`, hand it to `AlertNotifier`, then mark it `DELIVERED` and update the alert. On failure mark it `FAILED`, leave the alert and the index untouched, and retry the same quote on the next run | No silent loss. Every attempt is visible in `price_alert_triggers`. Retrying can't create duplicate rows | Delivery is at-least-once. A notifier that succeeds just before a crash can be called again |

## Decision

Record `PENDING`, notify, then update the alert. What decided it is that a missed alert is worse
than a repeated one. A repeat is bounded by `uq_price_alert_triggers_alert_quote (alert_id,
quote_at)`, so one quote can fire a given alert at most once. A retry updates the existing trigger
row instead of adding one. When `upsertPending` returns no id, that quote was already delivered
and the notifier is skipped.

The evaluator also doesn't choose channels. It only calls the `AlertNotifier` interface, and
`InAppAlertNotifier` was the first implementation.

## Consequences

- `delivery_status` (`PENDING`, `DELIVERED`, `FAILED`) on each trigger shows exactly which firings
  reached the customer.
- A failure on one alert never stops the other alerts for that quote. The quote is marked
  evaluated only when every alert for the symbol settled, so a single failure causes the whole
  symbol to be retried. The alerts that already fired are protected by `last_quote_at`.
- The interface paid off in the same sprint. `PreferenceAwareAlertNotifier` (email, SMS and in-app
  through the shared `NotificationDispatcher`) replaced the in-app-only notifier without any change
  to the evaluator.
- There is no separate notifications table for alerts. The UI bell polls
  `/price-alerts/triggers?since=` and shows the delivered triggers.
- An outage that keeps the notifier failing makes every run retry the same quotes. That load is
  bounded by the caps in 0012.
