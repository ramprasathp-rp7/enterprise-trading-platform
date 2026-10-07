# Security review: Sprint 10 extensions (preferences, notifications, watchlists, portfolio)

One combined review of the four modules added to the Trade REST API (`Services/order-service`).
It follows the shape of `TEMPLATE.md` in this folder, extended to the full OWASP Top Ten as the
Sprint 10 brief requires. Each finding names the module or modules it belongs to. Rows for a module
are added by its owners as that module is built. A category with no row for a module has not yet
been reviewed for it, and is not a finding of none.

## Header

| Field | Value |
|---|---|
| Service | Trade REST API, Sprint 10 extension modules |
| Date of review | 2026-10-06 |
| Commit reviewed | `feature/sprint10`, preferences module change set (on top of `d14289a`) |
| Version of the OWASP Top Ten used | 2021 |

## Changes the modules make to what the service already did

| Module | Change to existing behaviour |
|---|---|
| Preferences | Removed the old preferences routes (`/api/v1/accounts/{id}/preferences` free-form map, `/api/v1/preferences/defaults`) and their code (decision 0001). Added table `customer_preferences` (migration 014) and dropped the unused `accounts.preferences` column (migration 015). No filter, security configuration or route exemption changed. All new routes sit under the already-verified `/api/v1/**` prefix. Added a module-local `@MapperScan`; the application class was not edited |

## Categories

| Category | Module | In scope | Finding | Disposition |
|---|---|---|---|---|
| A01 Broken access control | Preferences | Yes | `GET`, `PUT` and `DELETE /api/v1/accounts/{accountId}/preferences` each call `AccountAccessAuthorizer.assertTokenCanAccess(accountId)` before touching data (`preferences/internal/web/PreferencesController.java`). Another account's preferences return 403 `ACC-403`, never an empty or default body | Fixed. Proven by `PreferencesControllerTest.anotherAccountsPreferences_areRefusedNotReturnedEmpty`, which asserts 403 on all three verbs and that the table was never touched, and by a manual curl with a real token against account 3 |
| A01 Broken access control | Preferences | Yes | Resolution route risk named in the brief: the channel lookup notifications needs returns decrypted contact details. As an HTTP route it would be readable with any customer token | Fixed by design (decision 0003). Resolution is the in-process `PreferenceResolver` interface with no route. `PreferencesModuleBoundaryTest` (ArchUnit) fails the build if any `@RestController` or `@Controller` depends on it, or if anything outside the module imports `preferences.internal..` |
| A01 Broken access control | Preferences | Yes | Consumer path: the resolver takes an `accountId` with no token, because it is called from a `trade-events` message, not an HTTP request | Accepted by design. The authorisation boundary for the consumer path is the event source. The Javadoc forbids passing a customer-supplied id. Recorded in 0003 |
| A02 Cryptographic failures | Preferences | Yes | Email addresses and phone numbers are personal data, and no other table on the platform holds them, so this module stores the only copy (decision 0002) | Fixed. AES-256-GCM, random 12-byte IV per write, account id bound as AAD (`ContactDetailCipher`). `ContactDetailCipherTest` covers wrong key, a row moved to another account, and key length. `PreferencesPersistenceRestartTest` asserts the stored bytes don't contain the plaintext. Checked manually in Postgres: 53-byte ciphertext, plaintext not present |
| A02 Cryptographic failures | Preferences | Yes | When `PREFERENCES_CONTACT_KEY` is unset, a published development key is used (a warning is logged at start-up) | Mitigated for development, outstanding for deployment (see below). The same convention as the accepted JWT dev secret. `.example.env` documents generating a key |
| A03 Injection | Preferences | Yes | None. Every statement in `PreferencesMapper` binds its values with `#{}`; there is no `${}` and no string concatenation. Inputs are a path `long`, an enum and a length-limited string validated by pattern | No action needed |
| A04 Insecure design | Preferences | Yes | Availability on the notification path. A failing store must neither silently pick a wrong channel nor stall every send | Fixed by design (decisions 0004, 0005). No preference gives the IN_APP default flagged `PLATFORM_DEFAULT`. On a store failure, the resolver serves the last known answer marked `stale`, or raises so the caller holds the message. Covered by `DefaultPreferenceResolverTest` |
| A04 Insecure design | Preferences | Yes | None for unbounded growth. `account_id` is the primary key, so each account has at most one row whatever the number of writes | No action needed |
| A05 Security misconfiguration | Preferences | Yes | None introduced. The security configuration is unchanged and no route was moved out of `/api/v1/**`. A malformed key fails start-up rather than running unencrypted | No action needed |
| A05 Security misconfiguration | Platform (pre-existing, affects all modules) | Yes | `GlobalExceptionHandler.handleGenericException` returns `"Error: " + ex.getMessage()` in a 500 body, so an unexpected exception's message reaches the client. Preferences exception messages were written to carry no personal data | Outstanding; owner to be agreed (shared file). Preferences side mitigated: crypto and validation messages never contain the value |
| A05 Security misconfiguration | Platform (pre-existing, affects all modules) | Yes | Request validation (`@Valid`) never runs in the running service. `pom.xml` declares `hibernate-validator` with `<scope>test</scope>`, which overrides the compile-scoped copy `spring-boot-starter-validation` brings, so the runtime classpath has no Bean Validation provider. Constraints on `OrderController`, `TransactionController` and `WatchlistController` bodies are enforced in unit tests and skipped in production. Found during the preferences live check: an empty channel list passed bean validation it should have failed | Fixed: removed the test-scoped `hibernate-validator` declaration, so the starter's compile-scoped copy (same version, 8.0.1.Final) is on the runtime classpath. Before and after, the same 38 requests were run against a freshly migrated scratch database. Every valid request answered identically. Only contract-invalid requests changed: orders with a 3-decimal price or a short idempotency key were accepted and are now 422; requests missing `side`, `idempotencyKey` or `accountId` returned 500 with a Java exception message and are now 422 `VAL-422`; invalid deposit amounts move from 400 `TRANS-400` to 422 `VAL-422`, as the existing unit tests expected. The deposit form now rounds to cents before sending, so a typed 10.555 still deposits 10.56 |
| A06 Vulnerable and outdated components | Preferences | Yes | Added test-scope dependencies only: `com.h2database:h2` (version managed by Spring Boot 3.4.0) and `com.tngtech.archunit:archunit-junit5:1.3.0`. Neither ships in the runtime classpath. No runtime dependency added | Accepted. A dependency audit of the service tree has not been run (no audit plugin is configured); see outstanding items |
| A07 Identification and authentication failures | Preferences | Yes | None introduced. The module relies on the existing HS256 verifier for `/api/v1/**`. Checked manually: no token gives 401, a forged signature gives 401, a valid token gives 200 | No action needed. Also repaired `SecurityFilterChainAuthFlowTest`, which was failing before this change because its test context lacked `JwtAuthenticationConverter`, so the verifier is again covered by a green test |
| A08 Software and data integrity failures | Preferences | Partly | The module consumes no events and deserialises no untrusted objects. The stored ciphertext is integrity-protected by the GCM tag | No action needed. A tampered row fails to decrypt (`DefaultPreferenceResolverTest.undecryptableRow_isTreatedAsUnavailable`) |
| A09 Security logging and monitoring failures | Preferences | Yes | Contact details must not reach logs. The existing handler already logs ACC-403 with requested and authenticated account ids | Fixed. Every type carrying a contact detail redacts it in `toString()`, validation messages never echo it, and resolver failures log account id and exception type only. Checked: the contact detail appeared 0 times across two service runs' logs after writes, reads and a restart |
| A10 Server-side request forgery | Preferences | Yes | Risk named in the brief: a customer-settable destination that the platform later posts to. The channel set is a closed enum (`EMAIL`, `SMS`, `IN_APP`), there is no URL or webhook channel, and contact details are pattern-validated. This module makes no outbound requests | Fixed by design. `PreferencesControllerTest.unknownChannel_isVal422` sends `WEBHOOK` with a metadata-service URL and gets 422 |

## Evidence

| Check | How it was performed | Result |
|---|---|---|
| Backend tests | `mvn test` in `Services/order-service` | 311 tests, 0 failures |
| Frontend tests | `npm run test:coverage` in `Frontend/trading-ui` | 575 tests passed; statements 90.7%, branches 87.2% |
| Route authorisation, live | Service on :8081 against local Postgres; HS256 token minted with the dev `JWT_SECRET` for account 2 | No token or forged token gives 401 `AUTH-401`. Account 2 gives 200. Account 3 gives 403 `ACC-403`. `WEBHOOK` gives 422 `VAL-422`. SMS with an email gives 422 `PREF-422`. The Sprint 6 `/accounts/2/balance` still gives 200 |
| Encryption at rest | `psql`: hex of `contact_detail_enc` searched for the plaintext | Not present; 53 bytes stored |
| Survives restart | Service stopped and restarted; GET repeated | Same preference returned. Also automated in `PreferencesPersistenceRestartTest` (file-based H2, real migration 014) |
| Request validation, before and after enabling the provider | Same 38 requests against a scratch database built by `apply.sh` (ASYNC mode, account 2), responses normalised and diffed | 20 valid requests identical. 18 differences, all contract-invalid inputs (see the A05 row) or their downstream balance and list effects |
| No PII in logs | `grep` for the test address across both service logs | 0 matches |
| Boundary | `PreferencesModuleBoundaryTest` (ArchUnit) | 3 rules pass |

## Outstanding items

| Item | Owner | Target date |
|---|---|---|
| Set a real `PREFERENCES_CONTACT_KEY` in every non-local environment (cloud week) | Preferences owner | Before the cloud-week deployment |
| Generic 500 handler echoes exception messages to clients (`GlobalExceptionHandler.handleGenericException`) | Team, to agree (shared file) | End of Sprint 10 |
| Run a dependency audit (for example, OWASP dependency-check) over the service and record the result | Team, to agree | End of Sprint 10 |
| Confirm decisions 0004 (default), 0005 (caller holds on raise) and 0007 (several channels, de-duplicate per eventId and channel) with the notifications pair | Preferences owner and notifications pair | 2026-10-06 |
| Confirm the default-account scope change (decision 0006) with the instructor | Preferences owner | 2026-10-06 |
