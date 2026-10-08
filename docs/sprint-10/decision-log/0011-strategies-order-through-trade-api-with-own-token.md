# 0011 Strategies place orders through POST /api/v1/orders using a short-lived token of their own

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Lavi Vasudevan |

## Context

A strategy fires when nobody is logged in, so it cannot borrow a customer's access token. The brief requires
orders to go through `POST /api/v1/orders`, never straight onto the `orders` topic and never by calling the
order service directly, because that route's validation, account authorisation (`AccountAccessAuthorizer`
compares the token's `accountId` claim) and idempotency check are what stand between a strategy bug and a bad
position.

## Options considered

| Option | For | Against |
|---|---|---|
| Store the customer's refresh token | Reuses the existing login | Long-lived; breaks silently on logout; widest blast radius |
| One shared service account | Simple | Bypasses per-account authorisation, the check we most want to keep |
| Mint a token per order for the strategy's own account | Same checks as a customer; expires in 60 seconds; identifiable | Needs the shared signing secret in the order service |

## Decision

Mint a 60-second HS256 token per order, signed with the existing `JWT_SECRET` and issuer, carrying the
owner's `accountId`, `roles: ["STRATEGY"]` and `strategyId`, and call the Trade API over HTTP with
idempotency key `STRAT-{strategyId}-{quote time}`. Creating or enabling the strategy while logged in, with an
explicit consent checkbox, is the customer's authorisation. The token can act on one account only and is
never stored. A 4xx is not retried; a 5xx or timeout is retried once with the same key; a reused key counts
as already placed.

## Consequences

The order service now signs tokens as well as verifying them, so a leaked secret also allows forged order
tokens (it already allowed forged logins). The call to its own service is deliberate and costs one local
round trip per order. Moving issuance to auth-service is the alternative if only it should ever sign.
