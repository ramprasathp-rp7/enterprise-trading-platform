# Integration Checklist: Auth Service with Trade REST API (Services/order-service)

This checklist provides step-by-step manual verification that the real Auth Service integrates with the Trade REST API (Services/order-service) with zero Java code changes.

**Prerequisite**: Both services must be running
- Auth Service: `npm start` (port 3000)
- Trade API: `mvn spring-boot:run` (port 8081)

---

## Phase 1: Token Issuance from Auth Service

### 1.1 Register a test user

Copy-paste this command to create a test user in the auth service:

```bash
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "review_user",
    "password": "Review@Pass123",
    "accountId": 1,
    "roles": ["CUSTOMER"]
  }'
```

**Expected Response** (HTTP 201):
```json
{
  "id": "<uuid>",
  "username": "review_user",
  "accountId": 1,
  "roles": ["CUSTOMER"]
}
```

---

### 1.2 Login and obtain tokens

Copy-paste this command to login and receive access + refresh tokens:

```bash
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "review_user",
    "password": "Review@Pass123"
  }' | jq .
```

**Expected Response** (HTTP 200):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "<opaque-token>",
  "expires_in": 900
}
```

**Save the access_token for the next steps:**
```bash
export TOKEN="<paste-access_token-here>"
```

---

### 1.3 Verify token claims (paste into https://jwt.io)

Copy the `access_token` value and paste it into **https://jwt.io** to decode and verify claims.

**Check these claims are present and correct:**
- `sub`: User ID (UUID string)
- `accountId`: 1 (number, must match request)
- `roles`: ["CUSTOMER"] (array of strings)
- `iat`: Issued at (current unix timestamp)
- `exp`: Expiry (iat + 900 seconds)
- `iss`: "auth-service" (must match JWT_ISSUER env var)

**Example decoded token:**
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "accountId": 1,
  "roles": ["CUSTOMER"],
  "iat": 1695234567,
  "exp": 1695235467,
  "iss": "auth-service"
}
```

---

## Phase 2: Token Acceptance by Trade REST API

### 2.1 Verify Trade API accepts valid token

Copy-paste this command to call a protected Trade API route with the valid token:

```bash
export TOKEN="<paste-access_token-from-1.2>"

curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Expected Response** (HTTP 200):
- If orders exist: `[{order object}, ...]` 
- If no orders: `[]` (empty array)
- **Critical**: Status should be **200 OK**, not 401 or 403

**This proves:** 
- ✅ Trade API validated the JWT signature
- ✅ Trade API verified the issuer claim matches JWT_ISSUER
- ✅ Trade API checked the token is not expired
- ✅ Trade API checked the accountId claim (matched against request)
- ✅ **Zero Java code changes** — all configuration-driven

---

## Phase 3: Token Rejection by Trade REST API

### 3.1 Test: No token is rejected

Copy-paste this command to call Trade API WITHOUT a token:

```bash
curl -X GET http://localhost:8081/api/v1/orders
```

**Expected Response** (HTTP 401):
```json
{
  "errorCode": "AUTH-401",
  "message": "Unauthorised"
}
```

**This proves:** Trade API requires authentication for `/api/v1/**` routes

---

### 3.2 Test: Malformed token is rejected

Copy-paste this command with an invalid token:

```bash
curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer not.a.valid.jwt"
```

**Expected Response** (HTTP 401):
```json
{
  "errorCode": "AUTH-401",
  "message": "Unauthorised"
}
```

**This proves:** Trade API rejects malformed tokens

---

### 3.3 Test: Token signed with wrong key is rejected

Generate a token signed with a different key (not the shared JWT_SECRET):

```bash
# This creates a token signed with a wrong secret key
node -e "
const jwt = require('jsonwebtoken');
const wrongSecret = 'wrong-secret-key-that-trade-api-should-reject';
const token = jwt.sign(
  {
    sub: 'test-user-id',
    accountId: 1,
    roles: ['CUSTOMER'],
    iss: 'auth-service',
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900
  },
  wrongSecret,
  { algorithm: 'HS256' }
);
console.log(token);
"
```

Copy the output token and use it:

```bash
export WRONG_TOKEN="<paste-generated-token>"

curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $WRONG_TOKEN"
```

**Expected Response** (HTTP 401):
```json
{
  "errorCode": "AUTH-401",
  "message": "Unauthorised"
}
```

**This proves:** 
- ✅ Trade API validates JWT signature using the shared JWT_SECRET
- ✅ Trade API rejects tokens signed with any other key
- ✅ Trade API checks signature **before** reading claims (symmetric verification)

---

### 3.4 Test: Expired token is rejected

Generate a token with expiry in the past:

```bash
# This creates a token that expired 30 minutes ago
node -e "
const jwt = require('jsonwebtoken');
const secret = 'test-shared-secret-32-bytes-minimum-key';
const now = Math.floor(Date.now() / 1000);
const token = jwt.sign(
  {
    sub: 'test-user-id',
    accountId: 1,
    roles: ['CUSTOMER'],
    iss: 'auth-service',
    iat: now - 3600,        // 1 hour ago
    exp: now - 1800         // 30 minutes ago (EXPIRED)
  },
  secret,
  { algorithm: 'HS256' }
);
console.log(token);
"
```

Copy the output token and use it:

```bash
export EXPIRED_TOKEN="<paste-generated-token>"

curl -X GET http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $EXPIRED_TOKEN"
```

**Expected Response** (HTTP 401):
```json
{
  "errorCode": "AUTH-401",
  "message": "Unauthorised"
}
```

**This proves:** 
- ✅ Trade API checks the `exp` claim
- ✅ Trade API rejects expired tokens even if signature is valid

---

## Phase 4: Verify Zero Java Code Changes

Confirm that the Trade REST API was not modified:

```bash
# Show the git status for Trade API
cd Services/order-service
git status

# Show the git diff (should be empty)
git diff src/

# Show commit log to verify no recent changes
git log --oneline -10 src/main/java/com/tradingplatform/api/security/
```

**Expected Output:**
- `git diff src/` returns empty (no changes)
- Git log shows no recent commits to security configuration

**This proves:** Integration required only configuration changes (root `.env`), not Java code changes

---

## Phase 5: Configuration Verification

### 5.1 Verify both services share the same JWT_SECRET

Check that root `.env` has the correct configuration:

```bash
grep -E "JWT_SECRET|JWT_ISSUER" ../.env
```

**Expected Output:**
```
JWT_SECRET=test-shared-secret-32-bytes-minimum-key
JWT_ISSUER=auth-service
```

**This proves:** Both services read identical configuration from the same `.env` file

---

### 5.2 Verify Trade API is using environment configuration

Check that Trade API's security config reads from environment:

```bash
grep -n "JWT_SECRET\|JWT_ISSUER" Services/order-service/src/main/java/com/tradingplatform/api/security/ApiSecurityConfiguration.java | head -5
```

**Expected Output:**
```
28:        @Value("${JWT_SECRET:test-shared-secret-32-bytes-minimum-key}") String jwtSecret,
29:        @Value("${JWT_ISSUER:auth-service}") String jwtIssuer
```

**This proves:** Trade API is configured to read JWT settings from environment at runtime

---

## Summary Checklist

| Check | Status |
|---|---|
| Auth Service: User registration succeeds (HTTP 201) | ☐ |
| Auth Service: Login returns access + refresh tokens (HTTP 200) | ☐ |
| Token contains all 6 required claims (sub, accountId, roles, iat, exp, iss) | ☐ |
| Trade API (Services/order-service): Accepts valid token from auth service (HTTP 200) | ☐ |
| Trade API: Rejects no token (HTTP 401 + AUTH-401) | ☐ |
| Trade API: Rejects malformed token (HTTP 401 + AUTH-401) | ☐ |
| Trade API: Rejects wrong-signed token (HTTP 401 + AUTH-401) | ☐ |
| Trade API: Rejects expired token (HTTP 401 + AUTH-401) | ☐ |
| Git diff shows zero Trade API Java changes (Services/order-service) | ☐ |
| Root `.env` has JWT_SECRET and JWT_ISSUER configured | ☐ |

---

## Key Insights

1. **No Java Code Changes**: The Trade REST API already had JWT verification via Spring Security OAuth2 Resource Server. Integration required only environment configuration.

2. **Shared Secret**: Both services read `JWT_SECRET` from the same root `.env` file. This is the only cryptographic material shared between services.

3. **Signature-Based Verification**: The Trade API verifies tokens by checking the HS256 signature using the shared secret. It does not need to connect to the Auth Service or any shared database.

4. **Issuer Validation**: Both services use `JWT_ISSUER=auth-service` to ensure tokens are from the trusted Auth Service, not from an attacker.

5. **Account Isolation**: The Trade API's `accountId` claim validation (already implemented) ensures users can only access their own trading accounts, even with a valid token.

---

## Troubleshooting

| Issue | Solution |
|---|---|
| Auth Service returns 401 on login | Verify username and password match what was registered |
| Trade API (sprint-07-event-backbone) returns 403 on valid token | Check that accountId in token matches the account in the request |
| Trade API returns 500 error | Check PostgreSQL connection and that Trade database is initialized; verify both Auth Service and Trade API are reading same JWT_SECRET |
| Wrong-key token test doesn't reject | Verify you used a different secret than `test-shared-secret-32-bytes-minimum-key` |
| Expired token test doesn't reject | Verify token's `exp` claim is in the past when decoded at jwt.io |
