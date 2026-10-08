# Security review: Trade REST API, Sprint 10 modules

One review of every module Sprint 10 added to the Trade REST API (`Services/order-service`) and the Angular UI (`Frontend/trading-ui`). Each category is answered for all modules at once, and a row is split only where a module came out differently. Every finding names its module and the file or route.

## Header

| Field | Value |
|---|---|
| Service | Trade REST API and trading UI, Sprint 10 |
| Reviewed by | Team 1 |
| Date of review | 2026-10-08 |
| Commit reviewed | `ec59b01` on `feature/sprint10` |
| Version of the OWASP Top Ten used | 2021 (all ten categories) |

### Modules reviewed

| Short name | Module | Code |
|---|---|---|
| **WL** | Watchlists and price alerts | `watchlist/`, `watchlist/alerts/` |
| **NOT** | Notifications (order outcomes, channel dispatch) | `notifications/` |
| **PREF** | Customer preferences (alert channels) | `preferences/` |
| **PNL** | Portfolio, P&L and account history | `api/controllers/PortfolioController`, `AccountController` (history), `PortfolioPnlService` |
| **ADV** | Trade advisor | `advice/` |
| **STR** | Strategy executor | `strategy/` |
| **CHT** | Candle and quote charts | `CandleChartController`, `MarketDataController`, `Candle*Service` |

Paths in this document are under `Services/order-service/src/main/java/com/tradingplatform/` unless stated otherwise.

## The two risks named for this week

| Risk | Where we looked | Result |
|---|---|---|
| An internal path that another module calls and that a customer's token can also reach | Every place one module calls another: preference resolution, price-alert evaluation, strategy evaluation and order placement, and the account-history snapshot | **Preference resolution** (decrypted contact details) has no HTTP route. `PreferenceResolver` is in-process only, and `PreferencesModuleBoundaryTest` fails the build if a controller depends on it.<br>**Alert and strategy evaluation** are `@Scheduled` jobs with no route.<br>**The account-history snapshot** is `POST /api/v1/admin/account-history/snapshot`, which needs `ROLE_ADMIN`.<br>**Strategies** reach `POST /api/v1/orders`, a route customers already use, with a token carrying the owner's `accountId`, so a strategy can do no more than its owner could. See A01 |
| A customer-supplied destination the platform then posts to | Anything a customer stores that the platform later sends to | **Contact details** (PREF) are the only one. The channel set is a closed enum (`EMAIL`, `SMS`, `IN_APP`); there is no URL or webhook channel; and email and E.164 phone formats are validated. The email and SMS senders are stubs that only log. **No outbound HTTP request** uses a customer value. See A10 |

## Categories

| Category | Module | In scope | Finding | Disposition |
|---|---|---|---|---|
| **A01** Broken access control | WL, NOT, PREF, PNL, ADV, STR | Yes | None. Every customer route calls `AccountAccessAuthorizer.assertTokenCanAccess(accountId)` before touching data. Checks against routes per controller: watchlists 6/6, price alerts 7/7, order notifications 1/1, preferences 3/3, portfolio 3/3, account history 2/2, advice 1/1, strategies 6/6. A token for another account gets `403 ACC-403`, never an empty list | No action needed. Covered by `PriceAlertControllerTest.everyRouteIsAuthorised` and `PreferencesControllerTest` (another account's preferences return 403, not an empty body) |
| A01 | STR | Yes | `StrategyTokenIssuer` mints a JWT with role `STRATEGY` and the owner's `accountId` so that strategies can trade while the owner is logged out. The authorizer checks only `accountId`, so that token would be accepted by every `/api/v1/**` route for that account, not only order placement | Accepted (team decision). The token lives for 60 seconds, is used for one call to `POST /api/v1/orders`, and is never stored or returned to a client. It can do only what the owner's own token can |
| A01 | CHT | Yes | `/api/v1/market-data/**` and `/api/v1/instruments/{id}/candles` need a valid token but are not scoped to an account | Accepted by design. They return public market data (prices and candles), the same for every customer |
| **A02** Cryptographic failures | PREF | Yes | Email addresses and phone numbers are personal data, and `customer_preferences` is their only copy | Fixed. AES-256-GCM with a random IV per write and the account id bound as AAD (`preferences/internal/crypto/ContactDetailCipher.java`). `ContactDetailCipherTest` covers a wrong key and a row moved to another account |
| A02 | PREF | Yes | If `PREFERENCES_CONTACT_KEY` is unset, a published development key is used (a warning is logged at start-up) | Mitigated for local development; outstanding for deployment (see Outstanding items) |
| A02 | All | Yes | If `JWT_SECRET` is unset, `api/security/ApiSecurityConfiguration.java` falls back to the published value `test-shared-secret-32-bytes-minimum-key`. Anyone who knows it could sign tokens for any account. `StrategyTokenIssuer` signs with the same secret | Mitigated: `.env` sets a real secret locally, and the algorithm is pinned to HS256. Outstanding: fail start-up when the variable is missing |
| A02 | UI | Yes | The access and refresh tokens are kept in `sessionStorage` (`core/auth/core-auth.service.ts`). localStorage holds only the notification list, the bell cursors and the theme | Accepted. Nothing in localStorage is a credential or contact detail; the notifications hold symbols, prices and order ids |
| **A03** Injection | All | Yes | None. Every MyBatis mapper in the seven modules binds its values with `#{}`. A search for `${` in mapper code found no matches; dynamic SQL uses `<if>` with bound values only. On the UI, nothing uses `innerHTML` or `bypassSecurityTrust*`, so alert reasons and notification text are rendered with Angular's escaped interpolation | No action needed |
| **A04** Insecure design | WL, STR, PREF, PNL | Yes | **Caps** stop one account from making the scheduled jobs do unbounded work:<br>- price alerts: 20 active and 100 in total per account (`ALR-409`);<br>- strategies: 10 active per account, one rule of each type per symbol;<br>- preferences: at most 3 channels;<br>- account history: at most 365 rows per request | No action needed |
| A04 | WL, ADV | Yes | **Watchlists have no cap**: `watchlist/service/WatchlistService.java` limits the name length but not the number of lists or items per account. The advisor (`GET /advice`) reads 120 candles for every watched and held symbol on each request, so a very large watchlist multiplies that work | Outstanding (see Outstanding items) |
| A04 | NOT, WL, STR | Yes | **Replays.** A redelivered Kafka message or a retried evaluation must not notify or trade twice | Fixed by design. Notifications are stored once (`order_notifications.event_id` is unique), and email and SMS go out only on the first store. Alert triggers are unique per (alert, quote). Strategy orders carry the idempotency key `STRAT-{id}-{quoteTime}`. Strategies also check `maxPosition`, `maxDailyNotional` and the shares held, and turn themselves off after repeated failures |
| A04 | WL, STR, PNL, CHT | Yes | **Availability.** The `RestTemplate` used for Fauxnance and candle sync (`config/JacksonConfig.java`) has no connect or read timeout. Spring's default scheduler has one thread, so one hung Fauxnance call would also stall price-alert evaluation, strategy evaluation and the history snapshot. The strategy order client does set a 5-second timeout | Outstanding (see Outstanding items) |
| **A05** Security misconfiguration | All | Yes | `api/exception/GlobalExceptionHandler.java` (`handleGenericException`) returns `"Error: " + ex.getMessage()` in the 500 body, so an unexpected exception's message reaches the client | Outstanding (shared file; see Outstanding items) |
| A05 | All | Yes | None for the rest. The security configuration was not loosened: every new route sits under the verified `/api/v1/**` prefix, `/api/v1/admin/**` still needs `ROLE_ADMIN`, CORS allows only `http://localhost:4200`, and Spring Actuator is not on the classpath | No action needed. The default-secret fallbacks are covered under A02 |
| **A06** Vulnerable and outdated components | UI | Yes | `npm audit --omit=dev`: 0 vulnerabilities in what ships to the browser. The full tree: 5 (2 high, 3 critical), all in development tooling (`@modelcontextprotocol/sdk`, `shell-quote` via `concurrently`, and the `@openapitools/openapi-generator-cli` chain) | Accepted for this sprint: none of these is in the built bundle. Outstanding: upgrade the tooling |
| A06 | Backend | Yes | Versions come from the Spring Boot 3.4.0 BOM. No dependency-audit plugin is configured, so the Java dependency tree has not been scanned | Outstanding: run OWASP dependency-check and record the result |
| **A07** Identification and authentication failures | All | Yes | None introduced. No module adds a login or token route. All of them rely on the existing HS256 verifier for `/api/v1/**`, which rejects a missing, forged or expired token with `401 AUTH-401`. Strategy tokens expire after 60 seconds | No action needed |
| **A08** Software and data integrity failures | NOT, CHT | Yes | The Kafka consumers (`trade-events` into notifications, `market-data` into the quote cache) deserialise JSON into fixed record types; polymorphic typing is not enabled anywhere. However, the broker has no authentication, so anything on the broker network could publish a forged `trade-events` message and create a false "order filled" notification | Accepted for local Docker (the broker is not exposed). Outstanding before cloud deployment: broker authentication |
| A08 | All | Yes | None for the build. `package-lock.json` is committed, the Angular clients are generated from committed contracts, and preference ciphertext is integrity-protected by its GCM tag (a tampered row fails to decrypt) | No action needed |
| **A09** Security logging and monitoring failures | All | Yes | None. Each module records what an incident review would need, without credentials or contact details:<br>- `ACC-403` logs the requested and the token's account;<br>- every fired alert is stored with its reason and delivery status;<br>- every strategy trigger is stored with its indicators, outcome and error;<br>- every order notification logs its channels.<br>The email and SMS stubs mask the address and number (`notifications/channel/*SenderStub.java`), and preference types redact contact details in `toString()` | No action needed |
| **A10** Server-side request forgery | All | Yes | None. The service makes three kinds of outbound request, and none takes a customer value in its URL:<br>- Fauxnance quotes: configured URL, symbols from the `instruments` table;<br>- candle sync: scheduled, with the same source of symbols;<br>- strategy orders: `trading.strategy.trade-api-url`, from configuration.<br>The only customer-supplied destination is a contact detail (see the named risks), and there is no URL or webhook channel | No action needed now. Review again when real email and SMS providers replace the stubs |

## Evidence

| Check | How it was performed | Result |
|---|---|---|
| Backend tests | `mvn test` in `Services/order-service` | 521 tests, 0 failures, 0 errors |
| Frontend tests | `ng test --watch=false` in `Frontend/trading-ui` | 76 files, 686 tests passed |
| Account check on every route | Counted `@*Mapping` against `assertTokenCanAccess` calls in each controller | All customer routes checked (counts in A01). Cancelling an order checks ownership inside `OrderService.cancelOrderById` |
| Live route checks (alerts, 2026-10-06) | Service against a scratch database, with HS256 tokens minted for two accounts | Create 201; duplicate rule 409 `ALR-409`; unknown symbol 404 `INS-404`; bad input 422 `VAL-422`; other account 403 `ACC-403` |
| SQL injection | Searched all mapper code for `${` | No matches |
| UI injection | Searched `Frontend/trading-ui/src/app` for `innerHTML` and `bypassSecurityTrust` | No matches |
| Dependencies | `npm audit --omit=dev`, then `npm audit` | 0 vulnerabilities shipped; 5 in development tooling |
| Outbound requests | Searched for `RestTemplate` and `RestClient` and traced each URL to its source | Configuration and the `instruments` table only |
| Encryption at rest (PREF, preferences owner, 2026-10-06) | `psql`: searched the hex of `contact_detail_enc` for the plaintext | Not present |
| No contact details in logs (PREF) | Read `AlertTarget` and `AlertChannelEntry` `toString()`, and the masking in both sender stubs | Destinations print as `<redacted>` or masked |
| Secrets defaults | Read `ApiSecurityConfiguration`, `ContactDetailCipher` and `application.properties` | Fallback defaults for `JWT_SECRET` and `PREFERENCES_CONTACT_KEY` (A02) |

## Outstanding items

| Item | Owner | Target date |
|---|---|---|
| Remove the `JWT_SECRET` fallback, so start-up fails when it is unset (A02) | Team (shared security config) | End of Sprint 10 |
| Set a real `PREFERENCES_CONTACT_KEY` in every non-local environment (A02) | Preferences owner | Before cloud deployment |
| Cap watchlists and items per account, as price alerts are capped (A04) | Watchlist owner | End of Sprint 10 |
| Add connect and read timeouts to the shared `RestTemplate`, and a scheduler pool larger than one thread (A04) | Market data / candles owner | End of Sprint 10 |
| Stop the generic 500 handler echoing exception messages to clients (A05) | Team (shared file) | End of Sprint 10 |
| Upgrade the development tooling flagged by `npm audit` (A06) | Frontend owner | End of Sprint 10 |
| Run OWASP dependency-check on the backend and record the result (A06) | Team | End of Sprint 10 |
| Turn on Kafka broker authentication (A08) | Team | Before cloud deployment |
