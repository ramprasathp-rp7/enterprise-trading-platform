# 0005 When the store is unreadable, resolution serves the last known answer, else raises and the caller holds

| Field | Value |
|---|---|
| Status | accepted, caller contract to confirm with the notifications pair |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner) |

## Context

Notifications calls the resolver synchronously, on the path that decides whether a customer hears
about their own trade. The call is in-process, so it won't time out, but the database read under it
can fail (connection pool exhausted, Postgres restarting), and so can decryption (wrong key,
tampered row). The notifications brief says the channel must come from preferences on every send,
not from a copy the caller takes at start-up. The brief also says availability matters more than
feature surface.

## Options considered

| Option | For | Against |
|---|---|---|
| Fail open: answer the IN_APP default on any error | Never raises | Quietly sends a customer who chose SMS to the inbox. The routing record would claim a choice that isn't theirs, and the outage is invisible |
| Fail closed: raise on any error | Honest; simple | One blip in the database stalls every notification, even for customers resolved seconds earlier |
| Read the store every time; on failure, serve the last answer seen for that account marked `stale = true`; raise `PreferencesUnavailableException` only if there is none | Survives short outages for active customers. Never invents a channel. A changed preference is used on the very next send, because the cache is never read while the store answers | An in-memory map of decrypted contact details for recently resolved accounts. Empty after a restart |

## Decision

The third option. `LastKnownChannels` is refreshed on every successful resolve and write and
cleared on reset. The resolver consults it only inside the failure branch. It won over failing open
because a wrong channel recorded as the customer's choice is worse than a delayed message.

## Consequences

- **Caller contract (Javadoc on `PreferenceResolver`):** on `PreferencesUnavailableException`, do not
  drop the message and do not guess a channel. Keep the notification queued and retry. It is
  already durably recorded before resolution, per the notifications brief.
- `stale = true` should be recorded on the notification so the demo and any incident review can see
  which sends used the fallback.
- Decryption failures are treated like an unreadable store. A tampered or moved row never yields
  another account's address, because the AAD binds the account id (see 0002).
- The fallback map holds decrypted personal data in memory. It is never logged (redacting
  `toString`) and is lost on restart, which we accept.
