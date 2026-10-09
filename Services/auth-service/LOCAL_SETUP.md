# Local Setup: Auth Service & Trade REST API Integration

This guide shows how to start both services locally to test end-to-end token integration.

## Prerequisites

- Node.js 20+ (for Auth Service)
- Java 21 (for Trade REST API)
- Maven 3.9+ (for Trade REST API)
- PostgreSQL 15+ running on `localhost:5432`
- Root workspace `.env` file configured with JWT settings (see repository root)

## Step 1: Prepare Databases

Ensure both databases exist and are initialized:

```bash
# From repository root
cd Database/postgres/order-service
./apply.sh              # Creates/initializes trading_system_db database

# Auth Service database may need initialization
cd ../auth-service
./apply.sh              # Creates/initializes auth service database
```

## Step 2: Start Auth Service

In one terminal:

```bash
cd Services/auth-service

# First run only: install dependencies
npm install

# Build the project
npm run build

# Start the service
npm start
```

Expected output:
```
[NestFactory] Starting NestApplication...
[InstanceLoader] AppModule dependencies initialized +XXms
Listening on port 3000
```

**Health check:**
```bash
curl -s http://localhost:3000/health
```

The service reads configuration from root `.env`:
- `JWT_SECRET=test-shared-secret-32-bytes-minimum-key`
- `JWT_ISSUER=auth-service`
- `PORT=3000`
- `AUTH_POSTGRES_DB=identity_access_management`

## Step 3: Start Trade REST API

In another terminal:

```bash
cd Services/order-service

# Start the service
mvn spring-boot:run
```

Expected output:
```
Started TradeApiApplication in X.XXX seconds
Tomcat started on port(s): 8081
```

**Health check:**
```bash
curl -s http://localhost:8081/actuator/health
```

The Trade API reads the same configuration from root `.env`:
- `JWT_SECRET` (must match Auth Service)
- `JWT_ISSUER` (must match Auth Service)
- Database credentials (shared PostgreSQL instance)

## Step 4: Verify Configuration Matching

Verify both services are using the same JWT configuration:

```bash
# Check root .env has both values set
grep -E "JWT_SECRET|JWT_ISSUER" ../.env
```

Both should output:
```
JWT_SECRET=test-shared-secret-32-bytes-minimum-key
JWT_ISSUER=auth-service
```

## Step 5: Obtain a Token from Auth Service

Get a token from the auth service login endpoint:

```bash
# Replace with actual demo user credentials (check auth service seeder for defaults)
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "demo_user",
    "password": "demo_password"
  }'
```

Response (JWT token in access_token field):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "...",
  "expires_in": 900
}
```

Copy the `access_token` value.

## Step 6: Call Trade REST API with Token

Use the token to call a protected Trade API route:

```bash
TOKEN="<paste_access_token_from_step_5>"

# GET a protected route
curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $TOKEN"

# Expected: 200 OK with order data (if any exist)
```

## Step 7: Verify Token Rejection

Test that Trade API rejects tokens without valid signature:

```bash
# Call without token
curl -X GET http://localhost:8081/api/v1/orders
# Expected: 401 Unauthorized with errorCode: AUTH-401

# Call with invalid token
curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer invalid.token.here"
# Expected: 401 Unauthorized with errorCode: AUTH-401

# Call with token signed by wrong key (see INTEGRATION_CHECKLIST.md for how to create one)
curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer <wrong_signed_token>"
# Expected: 401 Unauthorized with errorCode: AUTH-401
```

## Troubleshooting

### Auth Service fails to start with config error
- Check root `.env` has all required Auth Service variables
- Verify `JWT_SECRET` is at least 32 bytes
- Verify PostgreSQL connection works: `psql -h localhost -U postgres`

### Trade API fails to verify token from Auth Service
- Confirm both services are using **identical** `JWT_SECRET` and `JWT_ISSUER` from root `.env`
- Decode token at https://jwt.io and verify claims: `sub`, `accountId`, `roles`, `iat`, `exp`, `iss`
- Verify `iss` claim matches `JWT_ISSUER` env var (should be `auth-service`)

### Token accepted by Trade API but route returns 403
- May indicate account isolation issue (Trade API compares `accountId` claim with request param)
- Use integration tests to validate both JWT verification and account authorization

### Ports already in use
- Auth Service default: `3000` (set `PORT` env var to use different)
- Trade API default: `8081` (set `server.port` system property)

## Key Takeaway

No Java code changes are required in the Trade REST API (sprint-07-event-backbone). Token integration works through:
1. **Identical JWT_SECRET** passed to both services via root `.env`
2. **Identical JWT_ISSUER** to validate token issuer claim
3. **HS256 symmetric key signing** — Trade API verifies token by signature alone
4. **Account isolation** via `accountId` claim (already implemented in Trade API)
