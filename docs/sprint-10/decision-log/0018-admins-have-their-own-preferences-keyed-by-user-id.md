# 0018 Admins have their own preferences table, keyed by admin user id, and are notified when a customer is registered

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |
| Decided by | raahulkumardebug (preferences, notifications) |

## Context

Admins asked for the same choice of EMAIL, SMS and in-app that customers have (decisions 0001 to 0007), with email and
SMS covered by the same stubs (decision 0014). Customer preferences are keyed by `accounts.id`, but an admin has no
trading account: the seeded admin holds operator account 0, which is not a row in `accounts`, and every admin would
share it. Customer routes also compare the token's `accountId` with the path, which is meaningless for an admin.

Of the events an admin could be told about, only one was in scope: a customer being registered. That happens in the
auth service, which cannot reach the Trade API's notifications.

## Options considered

| Option | For | Against |
|---|---|---|
| Reuse `customer_preferences` with account 0 | No new table | All admins share one row, so one admin overwrites another. Its foreign key to `accounts` would reject account 0 |
| Store admin preferences in the auth service | Admins live there | A second preferences model and encryption key, and the Trade API's dispatcher cannot resolve them |
| A new `admin_preferences` table in the preferences module, keyed by user id | One row set per admin. Same rules, encryption and dispatcher. Nothing outside the module reaches it | A second table and a second resolver to maintain |

## Decision

Migration 024 adds `admin_preferences`, keyed by `(admin_user_id, alert_channel)` where the id is the token's `sub`.
It has the same rules and encryption as customer preferences, except that the ciphertext is bound to the admin user id
(`admin_preferences:<id>`), so a customer's encrypted contact cannot be decrypted as an admin's or the other way round.

- **Routes.** `GET`, `PUT` and `DELETE /api/v1/admin/preferences`, with no id in the path. The id always comes from
  the verified token (`AdminIdentity`), so one admin cannot read another's contact details. `ROLE_ADMIN` is enforced
  by the existing rule on `/api/v1/admin/**`.
- **Resolution.** `AdminPreferenceResolver` is an in-process interface like `PreferenceResolver` (decision 0003), and the same
  ArchUnit rule keeps controllers off it. It answers in-app only when nothing is stored or the store cannot be read, because
  admin notices are not queued for retry.
- **The one notice.** After the auth service registers a customer, the admin UI calls
  `POST /api/v1/admin/notifications/customer-registered`. The Trade API sends email and SMS through the stubs and answers with the
  admin's channels, and the UI adds the bell entry only if in-app is one of them. If that call fails, the bell entry is added
  anyway so the admin always sees something.
- **Frontend.** The existing Preferences page serves both roles at `/admin/preferences`, and the navbar link follows the role.

## Consequences

- The notice is sent by the browser after registration succeeds. If the admin closes the tab between the two calls, no
  email or SMS goes out. Having the auth service publish an event instead would remove that gap.
- The username in the notice is validated against the registration rules, so nothing else can be put into the message text.
- Only "customer registered" notifies admins. Other admin events would add a route, or an event consumer, on the same
  resolver.
- Admin preferences are not covered by the customer `ACC-403` account check, so authorisation here is the role plus
  "the token's own id". The contract documents `AUTH-403` for a token without the `ADMIN` role.
