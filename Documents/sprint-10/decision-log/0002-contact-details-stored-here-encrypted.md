# 0002 The preferences module stores contact details itself, encrypted with AES-256-GCM

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner) |

## Context

An alert channel needs a contact detail: an email address for EMAIL, a telephone number for SMS.
Both are personal data. The brief asks whether to store a reference to a copy held elsewhere or a
copy of our own. We searched the platform: `auth.users` holds id, username, password hash,
account id and roles; `accounts` holds holder name, balance and status. **No table on the platform
holds an email address or a telephone number.** The service shares its database connection with
the order book, and `contracts/kafka-topics.md` forbids email addresses in message payloads.

## Options considered

| Option | For | Against |
|---|---|---|
| Reference a contact detail held elsewhere | One copy; no reconciliation | Nothing exists to reference. We would have to add email/phone to `auth.users` in another team's service and contract (`auth-api.yaml`) during this sprint |
| Store plaintext in our own table, kept out of logs | Simplest; easy to query | A database dump, a backup or an over-broad SELECT from a module sharing the connection exposes every customer's address. The review would record it as an accepted risk |
| Store it in our own table encrypted with AES-256-GCM, key from the environment | A leaked row or dump is unreadable without the key. GCM detects tampering. Binding the account id as associated data stops a row being copied onto another account | Key management becomes our problem. The column can't be searched |

## Decision

Store the contact detail here, as AES-256-GCM ciphertext in `customer_preferences.contact_detail_enc`.
The key is `PREFERENCES_CONTACT_KEY`, there is a fresh 12-byte IV per write, and the AAD is
`customer_preferences:<accountId>`. A reference was not available, and between the two copies,
encryption was the only one that keeps a dump of a table sharing a connection with the order book
from being a disclosure.

## Consequences

- This module is the only owner of customer contact details on the platform. Notifications receives
  the decrypted destination only through `PreferenceResolver`, and must not persist or log it.
- Decryption happens in two places only: the owner's GET and the resolver.
  - `toString()` on every type carrying the value redacts it.
  - Validation messages never echo it.
- If the key is unset, a published dev key is used and a warning is logged at start-up, the same
  convention the team accepted for the JWT dev secret. A real deployment must set the key. That is
  listed as an outstanding item in the security review.
- Rotating the key needs a re-encryption job, which we have not built. Losing the key means every
  customer must re-enter their contact detail. We accept both costs for this sprint.
- We can't search by email (for example, to find duplicates). Nothing in scope needs that.
