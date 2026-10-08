# Secret-Free Production Bundle Implementation

## Overview

This document explains how the Trading UI application ensures no API keys, signing secrets, or market-data hosts are exposed in the production bundle.

## Architecture: Secret Management by Layer

### 1. Frontend Application (Sprint-09 - Trading UI)
**What it knows:**
- ✅ Auth Service URL (public endpoint)
- ✅ Trade API URL (public endpoint)
- ✅ JWT tokens stored in sessionStorage (obtained from Auth Service)

**What it NEVER knows:**
- ❌ Fauxnance API key
- ❌ JWT signing secret
- ❌ Database credentials
- ❌ Market data provider credentials

### 2. Backend Services

#### Auth Service (Sprint-08)
**Responsibilities:**
- Authenticates users
- Creates JWT tokens (signed server-side)
- **Stores:** `JWT_SECRET` in environment variables
- **Returns to frontend:** Signed JWT token only

#### Trade API / Event Backbone (Sprint-07)  
**Responsibilities:**
- Accepts orders from frontend
- Publishes to Kafka
- Returns order status

#### Order Executor Service
**Responsibilities:**
- Consumes orders from Kafka
- **Holds:** Fauxnance API key (server-side only)
- Prices against live market data
- Updates order status

### 3. API Call Flow

```
User Action (Frontend)
    ↓
[Frontend] Submit order
    ↓ (HTTP POST with JWT token)
[Auth Service] Validates token
    ↓ (token valid, routes request)
[Trade API] Accepts order, stores as NEW
    ↓ (publishes to Kafka)
[Kafka] orders topic
    ↓
[Order Executor] Consumes order event
    ↓ (uses Fauxnance API key - SERVER SIDE)
[Market Data] Query pricing
    ↓ (executor processes order)
[Database] Update order status
    ↓ (publishes status event)
[Kafka] trade-events topic
    ↓
[Frontend] Polls /blotter endpoint (server-side aggregation)
    ↓
User sees order status: NEW → FILLED/REJECTED
```

**Key Point:** Frontend never calls Fauxnance API. Prices reach the frontend through the Trade API, which received them from the Order Executor, which fetched them server-side using the hidden API key.

## Environment Configuration

### Development Build

```bash
npm run build --configuration development
```

Uses `environment.development.ts`:
```typescript
AUTH_SERVICE_URL: 'http://localhost:3000',
TRADE_API_URL: 'http://localhost:8081',
```

### Production Build

```bash
npm run build --configuration production
```

Uses `environment.production.ts`:
```typescript
AUTH_SERVICE_URL: process.env['AUTH_SERVICE_URL'] || 'https://api.yourdomain.com/auth',
TRADE_API_URL: process.env['TRADE_API_URL'] || 'https://api.yourdomain.com/trade',
```

**Note:** Environment variables are read at build time, not runtime. For runtime configuration, use a separate config API endpoint.

## Bundle Security Verification

### Pre-Deployment Checklist

Before deploying to production:

```bash
# 1. Build the bundle
npm run build

# 2. Run security verification
bash scripts/verify-bundle.sh

# 3. Manual verification
grep -r "api[_-]key\|jwt_secret\|fauxnance\|execute-api" dist/

# 4. Check source maps (optional, development debugging only)
ls -la dist/sprint-09-trading-ui/*.map
```

### What Gets Checked

✅ **Not in bundle:**
- `x-api-key`, `api_key`, `api-key` patterns
- `fauxnance` API references
- `execute-api.*.amazonaws.com` URLs
- `jwt_secret` or `JWT_SECRET` literals
- Database connection strings
- AWS credentials
- Any literal values of your Fauxnance key or signing secret

✅ **Safe to include in bundle:**
- Public API endpoints (localhost in dev, https:// in prod)
- JWT tokens obtained from Auth Service (not signing secret)
- Public configuration settings
- Feature flags
- Logging levels

## Secret Revocation Procedure

If a secret was ever accidentally committed to the repository:

### Immediate Actions
1. **Revoke the key**
   - Fauxnance dashboard: Rotate API key
   - JWT secret: Regenerate in Auth Service config
   - Database passwords: Change in database

2. **Do NOT just delete the line from code**
   - The old key may already be in production bundles
   - The old key may already be in user browsers
   - Revocation is the only way to invalidate exposed keys

### Follow-Up Actions
3. Update all affected services with new secrets
4. Deploy services with new credentials
5. Monitor for unauthorized access attempts
6. Audit logs for any usage of exposed key
7. Notify security team and stakeholders

## Security Policies

### 1. No Secrets in Frontend Code
- All external API calls requiring authentication happen server-side
- Frontend only calls public endpoints
- JWT tokens are obtained from Auth Service, never created client-side

### 2. Environment Isolation
- Development `.env` files never used in production
- Production environment variables injected at deployment time
- Different API endpoints for each environment

### 3. Source Maps in Production
- Source maps should NOT be deployed to production
- Build process: `outputHashing: "all"` + separate `.map` files
- `.map` files kept in artifact repository for debugging, not in CDN

### 4. Code Review Checklist
Before approving PRs:
- [ ] No hardcoded API keys
- [ ] No market-data endpoints
- [ ] No database credentials
- [ ] No JWT secrets
- [ ] All external API calls routed through backend services
- [ ] Environment configuration uses proper files

## Testing Security

### Unit Tests
```typescript
it('should not include fauxnance in environment', () => {
  expect(environment).not.toContain('fauxnance');
  expect(environment.AUTH_SERVICE_URL).toBeDefined();
  expect(environment.TRADE_API_URL).toBeDefined();
});

it('should use backend APIs only', () => {
  // Verify no direct calls to market-data APIs
  // Verify no AWS endpoints in HTTP client
});
```

### Integration Tests
```bash
# After building, verify bundle
npm run build
npm run verify-bundle  # Custom script
```
