# Customer preferences module

Sprint 10, first module in the chain preferences → notifications → watchlists. It owns how the
platform reaches a customer: one or more **alert channels** (`EMAIL`, `SMS`, `IN_APP`), each
with its **contact detail**. It runs inside the Trade REST API on 8081: no new port, no container,
no second token verifier.

## Boundary

```
com.tradingplatform.preferences            PUBLIC: the only package other modules may import
  PreferenceResolver                       resolve the channels for an account (in-process)
  ResolvedAlertChannels, AlertTarget       what it returns (one target per chosen channel)
  AlertChannel                             the closed set of channels
  PreferencesUnavailableException          raised only when the store is unreadable and nothing is cached
com.tradingplatform.preferences.internal   PRIVATE to this module
  web/          PreferencesController, request/response records (customer routes)
  service/      PreferencesService, DefaultPreferenceResolver, LastKnownChannels, ContactDetailRules
  persistence/  PreferencesMapper (customer_preferences only), PreferenceRecord
  crypto/       ContactDetailCipher (AES-256-GCM)
  config/       PreferencesModuleConfig (module-local @MapperScan)
```

`PreferencesModuleBoundaryTest` (ArchUnit) fails the build if:
- anything outside the module imports `..preferences.internal..`
- any controller depends on `PreferenceResolver`
- the module uses another module's mappers. Accounts are reached through `AccountService`.

## Customer routes

Contract: [`Frontend/trading-ui/contracts/preferences-api.yaml`](../../../../../../../Frontend/trading-ui/contracts/preferences-api.yaml).

| Route | Answer |
|---|---|
| `GET /api/v1/accounts/{accountId}/preferences` | 200 with the stored preferences, or a single default `IN_APP` entry with `configured: false` |
| `PUT /api/v1/accounts/{accountId}/preferences` | Full replace with `{"alertChannels": [{"channel", "contactDetail"}, ...]}` (1 to 3 entries, each channel once); 200 with the stored value |
| `DELETE /api/v1/accounts/{accountId}/preferences` | 204; the account goes back to the default |

- **Authentication:** every route is under `/api/v1/**`, so the service verifies the token first.
- **Authorisation:** every handler then compares the token's `accountId` claim with the path. A
  mismatch is 403 `ACC-403`.
- **Errors:** 401 `AUTH-401` and 404 `ACC-404`. 422 `VAL-422` when the body isn't valid JSON of this
  shape or names an unknown channel. 422 `PREF-422` when the list is empty or longer than three,
  names a channel twice, has an entry without a channel, or a contact detail doesn't fit its channel.
  These rules live in `ContactDetailRules`, not in bean-validation annotations, so they share one
  error code (see decision 0007).
- **Validation:** EMAIL needs an email address (max 254 characters). SMS needs an E.164 number;
  spaces, dashes, dots and brackets are stripped. IN_APP takes no contact detail.

## Internal resolution: how notifications calls in

```java
@Autowired PreferenceResolver preferences;   // inject the interface, never the implementation
ResolvedAlertChannels to = preferences.resolveAlertChannels(event.accountId());
for (AlertTarget target : to.targets()) {
    // deliver on target.channel() to target.destination(); record and de-duplicate per (eventId, channel)
}
```

| Situation | Answer |
|---|---|
| Customer stored channels | One target per channel with its decrypted `destination`, `source = CUSTOMER` |
| Nothing stored | A single `IN_APP` target with `destination = null`, `source = PLATFORM_DEFAULT` |
| Store unreadable, answer seen earlier | That answer with `stale = true` |
| Store unreadable, nothing seen | throws `PreferencesUnavailableException`. **Hold the message and retry; don't drop it or guess** |

Rules for callers:
- Resolve on every send; don't cache. A change the customer makes is used on the next call.
- Pass only an `accountId` from a trusted platform event, never one a customer supplied. No token
  is checked on this path.
- Deliver to every target, and de-duplicate on (eventId, channel) so a replay sends nothing twice.
- Record each target's `channel`, plus `source` and `stale`, on the notification. Never log or
  persist `destination`.

There is deliberately no HTTP route for resolution.

## What is stored

Table `customer_preferences` (migration `Database/postgres/order-service/migrations/014_create_customer_preferences.sql`):
- One row per (account, channel), at most three per account. No rows means the default applies. A
  save replaces the account's rows in one transaction.
- The contact detail is held only as AES-256-GCM ciphertext (`contact_detail_enc`), bound to the
  account id.
- No other table on the platform holds an email address or phone number, so this is the only copy.

Configuration: `PREFERENCES_CONTACT_KEY` is base64 of 32 bytes (`openssl rand -base64 32`). If it is
unset, a published development key is used and a warning is logged; never run that way with real
data.

## Decisions

In `docs/sprint-10/decision-log/`:
- 0001 own table, replacing `accounts.preferences`
- 0002 contact details stored here, encrypted
- 0003 resolution is an in-process interface
- 0004 no preference resolves to IN_APP
- 0005 store failure falls back, then raises
- 0006 no default-account preference (one user, one account)
- 0007 a customer may choose several alert channels

## Tests

| Test | Covers |
|---|---|
| `PreferencesServiceTest` | Save and read back, default when empty, validation matrix, ciphertext not plaintext |
| `PreferencesControllerTest` | Routes through the real `AccountAccessAuthorizer`; another account is refused, not returned empty |
| `PreferencesPersistenceRestartTest` | Write, close the context, open a new one on the same file-based H2 built from migration 014, read back |
| `DefaultPreferenceResolverTest` | Resolution contract: stored, default, stale fallback, raise |
| `ContactDetailCipherTest` | Encryption, wrong key, row moved between accounts |
| `PreferencesModuleBoundaryTest` | Module boundary and resolver off the wire |
