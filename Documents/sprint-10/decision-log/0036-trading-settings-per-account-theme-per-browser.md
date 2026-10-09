# 0036 Trading settings are stored per account; the colour theme stays in the browser; the order budget only warns

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-09 |
| Decided by | raahulkumardebug (preferences) |

## Context

The Preferences page grew from alert channels (decisions 0001 to 0007) into appearance and trading settings:
a light, dark or system theme; an order budget the order ticket warns about; a default order quantity; and
a market preference (USA, NSE or all markets) for instrument lists and searches. Admins have no trading
account (decision 0018). The theme already lived in `localStorage` and is applied by `index.html` before
Angular starts, to avoid a flash of the wrong colours.

## Options considered

| Option | For | Against |
|---|---|---|
| Everything in the browser | No backend work | Budget and market do not follow the customer to another browser; nothing server-side can use them later |
| Everything on the server, theme included | One place for all settings | The theme would arrive after the first paint, so every page load flashes; admins would need their own table |
| Trading settings per account on the server, theme in the browser | Settings that matter for trading follow the account; the theme applies before the first paint, for admins too | Two storage places |

And for the budget:

| Option | For | Against |
|---|---|---|
| Block orders above the budget | Strong guard-rail | A budget meant as a reminder would stop a deliberate trade |
| Warn and let the customer continue | Respects the customer's decision; matches "warning" in the request | Only as strong as the customer's attention |

## Decision

Budget, market and default quantity live in a new `account_settings` table (one row per account, migration
026), served by `GET/PUT/DELETE /api/v1/accounts/{id}/settings` with the usual account check. The theme stays
a per-browser setting, now with a "system" mode that follows the operating system. The order budget is in the
account's currency and only warns: the order ticket compares the order's estimated cost in the account's
currency with it, shows the warning, and relabels the button "Submit anyway".

## Consequences

- No row means the defaults: no budget, all markets, no default quantity.
- The budget check uses today's FX rate for foreign instruments; the cash moves at the rate at fill
  (decision 0008), so a borderline order can end up just over or under.
- Admin preferences show Appearance and Notifications only.
- The market preference only changes what is shown first; search offers "Show all markets", and exact symbol
  lookups (order ticket by URL, holdings) are not filtered.
