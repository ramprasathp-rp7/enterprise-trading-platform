# 0006 No default-account preference: one user holds one account, so there is nothing to choose

| Field | Value |
|---|---|
| Status | proposed, scope change to confirm with the instructor on day one |
| Date | 2026-10-06 |
| Decided by | Ram Prasath P (preferences story owner) |

## Context

Acceptance criterion 2 and the story ask for "a default account... applied at the customer's next
sign-in", and unit test path 4 is "the stored default is applied at next sign-in". The platform as
built by Sprint 8 ties every user to exactly one account:
- `auth.users.account_id` is `NOT NULL` with `CONSTRAINT uq_users_account_id UNIQUE`.
- The JWT carries a single `accountId` claim.
- Every route compares the path against that one claim.

The Angular user model has a single `accountId: number` and no account switcher. A customer
therefore cannot reach, select or open on any account but their own.

## Options considered

| Option | For | Against |
|---|---|---|
| Store `default_account_id`, validated to equal the token's account | Meets the wording of criterion 2 | Stores a value that can only ever equal the claim. "Applying" it at sign-in changes nothing. We would be demonstrating a no-op |
| Derive `defaultAccountId` read-only from the token and expose it in the response | Matches the wording without storage | A field presented as a preference that the customer can't set is misleading |
| Add multi-account support (auth schema, token claims, account switcher) | Makes the preference meaningful | Changes the auth contract, the token and every route's authorisation in a sprint where the auth service isn't ours to redesign. Far beyond one story |
| Drop the default-account preference and record the scope change | Honest. Keeps the module to what the platform can support | Criterion 2 and test path 4 are only partly met. The instructor must agree |

## Decision

Drop it. The preference record holds the alert channel and its contact detail. The account a
customer opens on is the one account their token names, as today. The deciding factor was that
every storable form of the preference is a constant on this platform, so building it would only add
code that demonstrates nothing.

## Consequences

- To bring to the instructor on day one: criterion 2 becomes "persists an alert channel per
  customer"; test path 4 ("stored default applied at next sign-in") is out of scope.
- The sign-in flow is unchanged.
- If multi-account support arrives (auth schema plus an account switcher), a `default_account_id`
  column and a sign-in hook can be added to this module without changing the resolver interface.
  That would supersede this entry.
