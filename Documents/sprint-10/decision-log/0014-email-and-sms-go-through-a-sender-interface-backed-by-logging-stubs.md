# 0014 Email and SMS go through one `ChannelSender` interface, backed by logging stubs

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | raahulkumardebug (notifications) |

## Context

Customers can choose EMAIL and SMS (decision 0007), and the notifications module has to honour that choice for
order outcomes and for price alerts. The project has no SMTP server, SMS gateway or provider account. What the sprint needs to show is that the
right message goes to the right channels at the right moment.

Two features send notifications today (order outcomes and price alerts), so the sending code could easily have
been written twice.

## Options considered

| Option | For | Against |
|---|---|---|
| In-app only, ignore EMAIL and SMS for now | No sender code | The preferences page would offer channels that do nothing, and the channel choice could not be demonstrated |
| A real provider (SMTP, an SMS gateway) | Messages are actually delivered | Needs accounts and secrets. Tests need the network. A provider outage becomes a platform problem |
| Stubs that log, behind one interface | The choice is visible in the logs. Needs no secrets. A real provider can replace a stub without touching callers | Nothing reaches a customer |

## Decision

`ChannelSender` has one method, `send(destination, message)`, and `EmailSenderStub` and `SmsSenderStub`
implement it by logging `[STUB] ... not sent (no provider yet)`. `NotificationDispatcher` finds the sender by
channel, and order notifications, price alerts (`PreferenceAwareAlertNotifier`) and admin notices (decision 0018)
all send through it. IN_APP is deliberately not a sender: the bell reads those rows from the database.

The stubs log the address or number masked (`ra***@example.com`, `+44*******123`), because the destination is
personal data and logs are copied around. The message wording (`NotificationDispatcher.messageFor`) follows
the bell's wording so a customer reads the same sentence on every channel.

## Consequences

- No email or text is ever delivered. The demonstration is the log line.
- To go live, replace a stub with a real implementation of `ChannelSender` and keep the masking. Callers do
  not change.
- A failure in one channel is caught and logged and does not stop the others, so a broken email address does
  not block the in-app notice or the SMS.
- There is no retry or outbox. A real provider failing would lose that channel's message, which is acceptable
  for logging stubs and would need a superseding entry before a real provider is added.
- An event sends externally only the first time it is stored (decision 0013), so redelivery never texts twice.
