# Auth Service

Signs users in for the Enterprise Trading Platform. It registers users, issues JWT access tokens and rotating
refresh tokens, and answers "who am I". The order and executor services trust its tokens (same secret and
issuer). NestJS 11 on Node.js, TypeORM on PostgreSQL.

Port: **3000** (from `Config/application.yaml`, `services.auth.port`).

## What it contains

| Folder (`src/…`) | What it does |
|---|---|
| `main.ts` | Starts the app: CORS for the UI, the error filter, validation (422 `VAL-422`), Swagger at `/docs` |
| `app/` | Root module wiring the feature modules together |
| `auth/` | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `GET /auth/me` |
| `tokens/` | JWT signing and verification, refresh-token rotation and family revocation |
| `users/` | User lookup, argon2id password hashing, demo user seeding |
| `database/` | TypeORM connection, `users` / `refresh_tokens` / `known_account_ids` entities and repositories |
| `config/` | Settings from environment variables and `Config/application.yaml` |
| `guards/` | JWT guard for protected routes |
| `common/` | JSON logger with secrets redacted, roles, error filter, `@CurrentUser` |

The contract is [`Contracts/auth-api.yaml`](../../Contracts/auth-api.yaml); the UI's generated client comes from
`Frontend/trading-ui/contracts/auth-api.yaml`.

## Setup

### Prerequisites

- Node.js 20 or later
- PostgreSQL 15+ with the auth schema applied (`Database/postgres/auth-service/apply.sh`)
- A `.env` at the repository root (copy `.env.example`)

### Install and run

```bash
cd Services/auth-service
npm ci
npm run build
npm start          # node dist/main.js, reading ../../.env
```

For development, `npm run dev` runs the TypeScript directly.

Once running:
- Swagger UI: `http://localhost:3000/docs`
- OpenAPI JSON: `http://localhost:3000/docs/json`

### Configuration

The port, allowed browser origin and database host/port/name come from `Config/application.yaml`
(path overridable with `PLATFORM_CONFIG_PATH`). Environment variables override it:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signing secret, at least 32 bytes (required; must match the order service) |
| `JWT_ISSUER` | Token issuer claim (default `auth-service`) |
| `PORT` | Overrides the port |
| `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `AUTH_POSTGRES_DB`, `PGPOOL_MAX` | Database connection |
| `ACCESS_TOKEN_LIFETIME` | Seconds (default 900, 15 minutes) |
| `REFRESH_TOKEN_LIFETIME` | Seconds (default 604800, 7 days) |

## Tests

```bash
npm test                                            # every spec
npx jest --testPathIgnorePatterns integration       # unit specs only
npm run test:cov                                    # with coverage, HTML in coverage/lcov-report/
```

`src/auth/auth.integration.spec.ts` is an integration test: it calls the auth service on port 3000 and the
Trade API on 8081 and registers a real test user, so it needs both running and changes that database.
See [`Documents/003_notes_on_testing_and_coverage.md`](../../Documents/003_notes_on_testing_and_coverage.md).

## Security

- Passwords hashed with argon2id
- JWTs signed with HS256 using `JWT_SECRET`
- Refresh tokens rotate on every use; reusing an old one revokes its whole family
- Login failures take the same time whether or not the user exists
- Secrets and tokens are redacted from logs
