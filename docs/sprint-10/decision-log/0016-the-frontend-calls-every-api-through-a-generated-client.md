# 0016 The Angular app calls the backend through clients generated from the OpenAPI contracts

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | raahulkumardebug (frontend integration) |

## Context

Each API is already written as an OpenAPI contract in `Frontend/trading-ui/contracts/` before its controller
(13 of them by the end of the sprint). The Angular app consumed them in two different ways. Some pages used
generated clients. Others, such as the transactions page, built URLs by hand
(`http://localhost:8080/api/v1/accounts/${id}/deposits`), kept their own copies of the request and response
types, and carried a hard-coded base URL. A global `HttpService` wrapper and a `SharedModule` existed but were empty.

The hand-written code could drift from the contract without anything failing: the transactions service's
comments listed `400` for an invalid amount, and were corrected to `422` only when it moved to the generated client.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep hand-written `HttpClient` services | Flexible. No generator step | Every field exists twice, so a rename compiles and then breaks at runtime. Base URLs repeated |
| One shared `HttpService` wrapper | A single place for headers and errors | It was empty. It would still need hand-written types per endpoint |
| Generate a typed client per contract (`typescript-angular`) | A renamed field is a compile error. Base URL set once per API. Types are never written twice | Large generated diffs. Needs the generator (Java) installed to regenerate |

## Decision

Every backend API the UI calls goes through the client generated from its contract
(`npm run generate`, one script per contract, output in `src/app/api/generated/<name>/`). Feature services
wrap the generated service, for example `TransactionService` now delegates to `TransactionsService`, and the
base URL for each API is set once in `app.config.ts` with `provide<Name>Api(environment.TRADE_API_URL)`.

What decided it is that the contract is the only thing the backend and frontend teams both read.
A generated client makes the build fail when they disagree, where a hand-written one makes the
user find out.

In the same change the empty `HttpService`, the unused `SharedModule`, and the `quotes-api` contract and
client were deleted. The market-data contract is the one the UI uses for quotes.

## Consequences

- Changing a contract means running the matching `npm run generate:<name>` and committing the regenerated
  client, so API changes produce large diffs under `src/app/api/generated/`.
- Regenerating needs `@openapitools/openapi-generator-cli`, which runs the generator JAR and so needs Java.
- No feature code calls `HttpClient` for a contract endpoint any more. The admin registration page uses the
  generated `AuthService.register`. `TokenRefreshService` uses `AuthService.refresh`, but builds the client on
  `HttpBackend` instead of injecting it, so `/auth/refresh` still skips the interceptors. An injected client
  would send the refresh back through `AuthInterceptor`, which depends on `TokenRefreshService`. A test checks
  that no interceptor sees the refresh call.
