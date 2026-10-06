# 0003 Channel resolution is the in-process PreferenceResolver interface, with no HTTP route

| Field | Value |
|---|---|
| Status | accepted, interface shared with the notifications pair for agreement |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner); notifications pair to confirm |

## Context

Notifications consumes `trade-events` and has to know where to reach the customer on every send.
Both modules run inside the Trade REST API. Every route under `/api/v1/**` accepts any valid
customer token, and the only per-route protection is the `accountId` claim check. The consumer acts
on a Kafka message and holds no customer token at all. A resolution path returns a decrypted email
address or phone number.

## Options considered

| Option | For | Against |
|---|---|---|
| HTTP route, e.g. `GET /api/v1/internal/preferences/{accountId}/channel` | Familiar; callable from another service later | Reachable by any customer holding a token. It either leaks other customers' contact details, or needs a service identity and a second authorisation scheme. The consumer would also need a token it doesn't have |
| Notifications reads `customer_preferences` directly | No code needed in this module | It's the boundary violation the brief names. It skips decryption and the default rule, and gives two modules ownership of one table |
| Published Java interface `com.tradingplatform.preferences.PreferenceResolver`, implementation in `preferences.internal` | Not on the wire, so it can't be called with a customer token. One place applies decryption, the default and the failure rule. It can be stubbed early and replaced later | Can't be called across a process boundary; if notifications moves to its own service, we need a new design |

## Decision

Publish `PreferenceResolver` (plus `ResolvedAlertChannels`, `AlertTarget`, `AlertChannel` and
`PreferencesUnavailableException`) in the module's root package and keep everything else under
`preferences.internal`. The deciding factor was that an HTTP route cannot be made safe here without
inventing service-to-service authentication, while an interface simply has no attack surface.

## Consequences

- Authorisation boundary for the consumer path: the resolver trusts its `accountId` argument
  because it comes from a platform event, not a customer request. Its Javadoc says never to pass it
  an id a customer supplied.
- Enforced by `PreferencesModuleBoundaryTest` (ArchUnit):
  - nothing outside the module imports `preferences.internal..`
  - no `@RestController` or `@Controller` depends on `PreferenceResolver`
  - the module never uses `api.mappers..`
- Notifications must depend only on `com.tradingplatform.preferences.*`, the root package.
- If a module is ever split into its own service, this seam has to be redesigned, and that would be
  a new decision.
