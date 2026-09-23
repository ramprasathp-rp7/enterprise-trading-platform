# Sprint 08: Auth Service

Authentication service for the Enterprise Trading Platform. Provides JWT token issuance, validation, and refresh token rotation.

## Quick Start

### Prerequisites
- Node.js 20 or later
- PostgreSQL 15+ (shared instance)
- Environment variables configured in `../.env` (workspace root)

### Setup

1. **Install dependencies:**
   ```bash
   npm ci
   ```

2. **Build:**
   ```bash
   npm run build
   ```

3. **Run tests:**
   ```bash
   npm test
   ```

4. **Start the service:**
   ```bash
   npm start
   ```

The service listens on port `3000` by default.

## Development

```bash
npm run dev              # Run with ts-node for development
npm run test:watch      # Run tests in watch mode
npm run test:cov        # Generate coverage report
```

## API Documentation

Once running, access:
- Interactive UI: `http://localhost:3000/docs`
- OpenAPI JSON: `http://localhost:3000/docs/json`

## Environment Variables

This service is configured to load environment variables from `../.env` (workspace root), not from `sprint-08-auth-service/.env`.

Key variables:
- `JWT_SECRET` - Must be at least 32 bytes (required)
- `JWT_ISSUER` - Token issuer claim (default: `auth-service`)
- `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `AUTH_POSTGRES_DB` - Database connection
- `ACCESS_TOKEN_LIFETIME` - Seconds (default: 900 = 15 min)
- `REFRESH_TOKEN_LIFETIME` - Seconds (default: 604800 = 7 days)
- `AUTH_RUN_MIGRATIONS` - Run migrations on startup (default: false)

## Project Structure

```
src/
  app/                 # NestJS application module
  config/              # Environment and configuration validation
  database/            # TypeORM connection and migrations (Phase 2)
  common/
    logging/           # JSON logger with sensitive data redaction
  users/               # User registration and password handling (Phase 3)
  tokens/              # JWT token service and refresh logic (Phase 4)
  auth/                # Controller and endpoints (Phase 5)
  guards/              # JWT authentication guard (Phase 6)
```

## Contract

Implements `contracts/auth-api.yaml` exactly:
- `POST /auth/register` - Register a new user (no tokens issued)
- `POST /auth/login` - Login and receive token pair
- `POST /auth/refresh` - Refresh access token
- `GET /auth/me` - Get authenticated user profile (protected)

## Security

- Passwords hashed with argon2id
- JWT signed with HS256 and JWT_SECRET
- Refresh tokens rotated on every use
- Timing-safe login failure responses
- Sensitive fields redacted from logs
