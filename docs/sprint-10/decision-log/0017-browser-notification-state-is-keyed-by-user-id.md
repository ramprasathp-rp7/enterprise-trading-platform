# 0017 Notification state kept in the browser is keyed by user id, not username or account

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |
| Decided by | raahulkumardebug (notifications) |

## Context

The notification bell keeps three things in `localStorage`: the bell's list, a "welcome shown" flag, and the
feed cursors that stop the order and price-alert watchers replaying history (decision 0013). Before this change
they were keyed by **username** (the list and the welcome flag) and by **account id** (the cursors).

A user reported seeing "order placed" notifications straight after logging in for the first time. The old keys
allowed two causes:

- The username was reused: a user re-created with the same name (after a database reset, or a different person)
  inherited the previous user's bell list and found the welcome flag already set.
- The account id was shared: two users of one account in the same browser share one cursor, so a new user
  starts from an old position and is shown any outcomes from the last hour.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep the keys, clear storage on logout | Nothing to migrate | Logging out from another tab, or a crash, skips the clear. The state is lost for the legitimate user too |
| Key by account id | Matches the data the feeds are about | An account can have several users over time, and an admin has account 0 |
| Key by user id (the token's `sub`) | Unique for a user. Never reused when a username is. Already in the token | Old entries are orphaned |

## Decision

All three are keyed by the user's id, with the username as a fallback if a token has no id:
`etp-notifications:<userId>`, `etp-welcomed:<userId>`, and `etp-order-notification-cursor:<userId>:<accountId>`
(and the price-alert cursor likewise). A shared helper, `storageOwner()`, is the one place that chooses the key.
The preferences store compares the same key to decide when to forget the previous user's data.

A user id is the only identifier that cannot be reused by someone else. Account id was kept in the cursor key
too, because a feed belongs to an account as well as to the person reading it.

## Consequences

- A re-created user, or a new user on the same account, starts with an empty bell and gets a fresh welcome.
- Existing users see the welcome once more and an empty bell after this ships, because their old entries sit
  under the old keys.
- The old entries are never cleaned up. They are small and ignored.
- State is still per browser, as before. Nothing is stored on the server, so a second browser has its own bell.
- Tests cover a re-created user with the same username, a fallback to the username, and a user on an
  account another user has used.
