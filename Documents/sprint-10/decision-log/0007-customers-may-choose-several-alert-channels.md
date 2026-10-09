# 0007 A customer may choose several alert channels, with one contact detail per channel

| Field | Value |
|---|---|
| Status | accepted in preferences; resolver shape to confirm with the notifications pair |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner) |

## Context

The first version stored exactly one alert channel per customer. A customer who wants a rejected
order by SMS and also a record in their in-app inbox could not have both. The preferences brief
asks for "at a minimum ... an alert channel with its contact detail". It lists "multiple contact
points per channel" (two email addresses, say) as out of scope. The notifications brief requires
"delivery on at least one channel" and that the message goes out on the channel preferences holds.
Nothing had been built against the single-channel resolver yet, and migration 014 had not been
committed, so the shape could still change cheaply.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep one channel per customer | Smallest seam: notifications makes one send per event | A customer has to choose between being reached quickly (SMS) and having a record (inbox) |
| Several channels, one contact detail per channel (rows keyed on account and channel) | Meets the brief's "at a minimum" and stays clear of the out-of-scope "multiple contact points per channel". At most three entries, bounded by the enum | The resolver returns a list, so notifications sends and records once per channel and de-duplicates on (eventId, channel) |
| Several channels and several addresses per channel | Most flexible | Explicitly out of scope in the brief. Needs an address book, labels and a primary flag. More personal data held |

## Decision

Several channels, each at most once, one contact detail each. The deciding factor was that the
brief's minimum allows it, its out-of-scope line is about addresses rather than channels, and the
change was still cheap because nothing yet depended on the single-channel shape.

## Consequences

- **API:**
  - `PUT` takes `{"alertChannels": [{"channel", "contactDetail"}, ...]}` with 1 to 3 entries.
  - An empty list, a duplicate channel or a missing channel is `PREF-422`.
  - Responses list the channels in EMAIL, SMS, IN_APP order.
  - The default is a single IN_APP entry (0004 still holds).
- **Storage:** `customer_preferences` has one row per (account, channel), and a save deletes and
  re-inserts the account's rows in one transaction. A failed save leaves the previous choice in
  place. Zero channels is refused rather than stored, because a customer who chose nothing would
  never hear about a rejected order.
- **Seam change for notifications:**
  - `PreferenceResolver.resolveAlertChannels(accountId)` returns `ResolvedAlertChannels` with one
    `AlertTarget` per channel.
  - Notifications must deliver to each target and record each target's channel.
  - Its idempotency key becomes (eventId, channel), so a replay sends nothing twice on any channel.
  - This is a bigger interface than the single answer agreed on Monday, so the notifications pair
    must confirm it before building.
- **Alert volume:** a watchlist alert that fires once is now delivered up to three times, once per
  channel. That is the customer's choice, but watchlists should keep its decision to fire once per
  crossing rather than on every quote.
- **Validation:** all of these rules live in the service (`ContactDetailRules`), not in
  bean-validation annotations, so every one of them answers the same code, `PREF-422`. While this
  was built we found the service had no Bean Validation provider at runtime, so `@Valid` was
  skipped in production. That is now fixed (security review, A05), but keeping the rules in one
  place still gives one error code and one tested path.
