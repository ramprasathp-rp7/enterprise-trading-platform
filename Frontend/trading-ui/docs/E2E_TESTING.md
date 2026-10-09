# Playwright E2E Testing Guide

## Overview

This project uses Playwright for end-to-end testing of the trading UI. Tests run against a real running stack with all backend services active.

**Test Coverage:** 3 independent journeys with 45 total test cases (15 per journey × 3 browsers: chromium, firefox, webkit)

## Test Journeys

### 1. Sign-In Journey (`e2e/sign-in.spec.ts`)
- **Purpose:** Validate authentication flow
- **Test Cases:**
  - Guard redirect to `/sign-in` with `returnUrl` parameter
  - Invalid credentials show error and don't store token
  - Valid credentials store JWT token and redirect to dashboard
  - Post-login redirect to return address works
- **Status:** ✅ All 12 tests passing (4 tests × 3 browsers)

### 2. Place Order Journey (`e2e/place-order.spec.ts`)
- **Purpose:** Validate order ticket form and submission
- **Test Cases:**
  - Account field is read-only
  - Form validation (quantity > 0, price > 0 with 2 decimals max, symbol required)
  - Invalid orders rejected with error message
  - Valid orders accepted and transition through status lifecycle
  - Fresh session has no shared state between tests
- **Status:** 🟡 Code complete, order executor processing required

### 3. Blotter Journey (`e2e/blotter.spec.ts`)
- **Purpose:** Validate order history display and status management
- **Test Cases:**
  - Order history loads with existing orders
  - Status badges display correctly (NEW, FILLED, REJECTED, CANCELLED)
  - Orders appear in newest-first ordering
  - NEW status orders show refresh action
  - Rejected orders included in history
  - Fresh session is independent
- **Status:** ✅ Successfully updated with proper patterns

## Prerequisites

All services must be running:

```bash
# Terminal 1: Auth Service (port 3000)
cd sprint-08-auth-service
npm run build && npm start

# Terminal 2: Trade API / Event Backbone (port 8081)
cd sprint-07-event-backbone
mvn spring-boot:run

# Terminal 3: Order Executor (processes orders from Kafka)
cd order-executor-app
mvn spring-boot:run

# Terminal 4: PostgreSQL (default connection assumed in services)
psql -U postgres
```

## Running Tests

```bash
# Install dependencies
npm install

# Run all tests (all browsers in parallel)
npm run e2e

# Run tests in a specific browser
npm run e2e -- --project=chromium
npm run e2e -- --project=firefox
npm run e2e -- --project=webkit

# Run specific test file
npm run e2e -- sign-in.spec.ts

# Run with single worker (slower but easier to debug)
npm run e2e -- --workers=1

# Run with visible browser
npm run e2e -- --headed

# Debug mode (interactive browser + inspector)
npm run e2e -- --debug
```

## Key Patterns

### Environment Configuration

Tests use environment variables for URLs:
- `E2E_BASE_URL` - Application base URL (default: `http://localhost:4200`)
- `E2E_USERNAME` - Test user username (default: `test_user_1`)
- `E2E_PASSWORD` - Test user password (default: `Test@Password1`)

```bash
E2E_BASE_URL=http://localhost:4200 npm run e2e
```

### Test Structure

**Single wait pattern (no polling):**
```typescript
// ✅ GOOD: One attempt to wait for redirect
try {
  await page.waitForURL(url => url.pathname.includes('/dashboard'), { timeout: 10000 });
} catch (e) {
  console.log('⏳ No redirect, checking token...');
}
```

**Credential extraction:**
```typescript
const token = await page.evaluate(() => sessionStorage.getItem('access_token'));
if (!token) throw new Error('Sign-in failed');
```

**Independent journeys:**
Each test signs in with fresh credentials and navigates independently. No shared state between tests.

## Configuration

### `playwright.config.ts`
- **Browsers:** chromium, firefox, webkit (parallel execution)
- **Timeout:** 60 seconds per test
- **Retries:** On-first-retry only with trace collection
- **WebServer:** Automatically starts `ng serve` with 120s timeout

### Build Configuration

Environment configuration uses Angular's fileReplacement pattern:
- **Development:** Uses `environment.ts` (localhost URLs)
- **Production:** Replaced with `environment.production.ts` (HTTPS URLs)

See [Secret Management](./SECRET_MANAGEMENT.md) for production security details.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Tests timeout waiting for redirect | Check Auth Service is running on port 3000 |
| "Element not found" errors | Verify test selectors match current HTML (e.g., `data-testid="account-input"`) |
| Order status never transitions | Ensure Order Executor is running and consuming from Kafka |
| SecurityError accessing sessionStorage | Add `await page.goto(E2E_BASE_URL)` before accessing storage |
| Browser crashes during tests | Avoid retry loops with `page.waitForTimeout()` - use single wait attempts |

## CI/CD Integration

Tests are designed to run in automated pipelines:
```bash
# Full validation
npm run build           # Production bundle
npm run e2e             # All E2E tests
bash scripts/verify-bundle.sh  # Security audit
```

## Files

- **Tests:** `e2e/*.spec.ts`
- **Configuration:** `playwright.config.ts`
- **Verification:** `scripts/verify-bundle.sh`
- **Security Documentation:** `docs/SECRET_MANAGEMENT.md`
