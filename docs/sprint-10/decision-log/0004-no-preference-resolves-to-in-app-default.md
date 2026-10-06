# 0004 With no stored preference, resolution answers IN_APP, flagged as the platform default

| Field | Value |
|---|---|
| Status | proposed, to agree with the notifications pair |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner) |

## Context

Every account starts with no row in `customer_preferences`. Customers placing orders on Monday
will not have visited the settings screen. The notifications brief says choosing nothing means the
message is lost and nobody finds out; both "hold" and "send on a documented default" are
defensible. The Angular app already has a notification inbox, and notifications will expose
history to the customer anyway.

## Options considered

| Option | For | Against |
|---|---|---|
| Return empty / NOT_CONFIGURED and let notifications decide | Least opinionated | Pushes a product decision onto the other pair. Every caller has to handle "nothing", and a forgotten branch loses the message |
| Hold until the customer chooses | Never sends somewhere unwanted | A customer who never opens settings never hears about a rejected order, which is the failure the brief opens with |
| Answer `IN_APP`, no destination, `source = PLATFORM_DEFAULT` | Always deliverable. Needs no personal data. Records a channel the demo can point at. The `source` flag still lets notifications choose to hold | IN_APP only reaches a customer who signs in; it is not a push |

## Decision

`resolveAlertChannels` never returns null or an empty list and never raises for "not configured".
It answers `ResolvedAlertChannels.platformDefault(accountId)`, a single IN_APP target. The customer GET mirrors this with
`configured: false`, never an empty body or a 404. IN_APP won over holding because the inbox is the
one channel that works for every customer with no data from them, so no outcome is lost.

## Consequences

- Notifications records `channels = [IN_APP], source = PLATFORM_DEFAULT` on the notification, which
  makes it demonstrable which messages went to the default.
- The settings page shows a banner when `configured` is false, so the customer knows where alerts
  are going.
- `DELETE` returns an account to this state.
- If the notifications pair prefers to hold on a default, they can do so from the `source` flag with
  no change here. If they want a different default channel, that is a one-line change in
  `ResolvedAlertChannels.platformDefault` plus a superseding entry.
