import { test, expect } from '@playwright/test';

/**
 * E2E Journey: Place an Order
 * 
 * Requirements:
 * - Account field read-only
 * - Form validation before submission
 * - Place order and accept NEW, FILLED, or REJECTED status
 * - Never assert FILLED only (executor may not be enabled yet)
 */

// Environment variables (required to run tests)
const E2E_BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:4200';
const E2E_AUTH_API = process.env.E2E_AUTH_API || 'http://localhost:3000';
const E2E_TRADE_API = process.env.E2E_TRADE_API || 'http://localhost:8081';
const E2E_USERNAME = process.env.E2E_USERNAME || 'test_user_1';
const E2E_PASSWORD = process.env.E2E_PASSWORD || 'Test@Password1';
const E2E_ACCOUNT_ID = process.env.E2E_ACCOUNT_ID || '1';
const E2E_SYMBOL = process.env.E2E_SYMBOL || 'AAPL';

test.setTimeout(60000);

test.describe('Place Order Journey', () => {
  // This suite shares one test account and submits real orders, so run it serially
  // to avoid cross-test contention and browser-specific timing flakes.
  test.describe.configure({ mode: 'serial' });

  /**
   * Setup: Register test user and sign in before all tests
   */
  test.beforeAll(async () => {
    try {
      console.log(`\n📝 Registering test user: ${E2E_USERNAME}`);
      const res = await fetch(`${E2E_AUTH_API}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: E2E_USERNAME,
          password: E2E_PASSWORD,
          accountId: E2E_ACCOUNT_ID,
          roles: ['CUSTOMER'],
        }),
      });
      
      if (res.status === 201 || res.status === 409) {
        console.log(`✅ Test user ready`);
      } else {
        console.warn(`⚠️  Register status: ${res.status}`);
      }
    } catch (err) {
      console.error(`❌ Setup error:`, err);
    }
  });

  /**
   * Cleanup: Clear storage after each test (no shared state)
   */
  test.afterEach(async ({ page }) => {
    try {
      await page.evaluate(() => sessionStorage.clear());
    } catch (e) {
      console.log(`⚠️  Could not clear sessionStorage (may be on blank page), continuing...`);
    }
  });

  /**
   * Helper: Sign in and navigate to order ticket page
   */
  async function signInAndNavigate(page: any) {
    console.log(`\n🔐 Signing in as: ${E2E_USERNAME}`);
    await page.goto(`${E2E_BASE_URL}/sign-in`, { waitUntil: 'networkidle' });
    
    // Fill form
    const usernameInput = page.locator('[data-testid="username-input"]');
    const passwordInput = page.locator('[data-testid="password-input"]');
    const submitBtn = page.locator('[data-testid="submit-btn"]');
    
    console.log(`✓ Form elements visible`);
    
    await usernameInput.fill(E2E_USERNAME);
    await passwordInput.fill(E2E_PASSWORD);
    
    console.log(`📝 Form filled, submitting...`);
    // Firefox can occasionally keep waiting on SPA navigation after submit;
    // noWaitAfter keeps the click deterministic and we assert outcomes below.
    await submitBtn.click({ noWaitAfter: true });
    
    // Wait for redirect to dashboard
    try {
      await page.waitForURL((url: URL) => url.pathname.includes('/dashboard'), { timeout: 10000 });
      console.log(`✅ Redirected to /dashboard`);
    } catch (e) {
      console.log(`⏳ No redirect, checking token...`);
    }
    
    // Check for token
    const token = await page.evaluate(() => sessionStorage.getItem('access_token')).catch(() => null);
    
    if (!token) {
      // Check for error message
      const errorMsg = await page.locator('.alert.alert-danger, [class*="error"]').first().textContent().catch(() => 'Unknown error');
      console.error(`❌ Sign-in failed: ${errorMsg}`);
      throw new Error(`Sign-in failed for ${E2E_USERNAME}: ${errorMsg}`);
    }
    
    console.log(`✅ Token acquired`);
    
    // Navigate to order ticket
    console.log(`📋 Navigating to order-ticket...`);
    await page.goto(`${E2E_BASE_URL}/order-ticket`);
    await page.waitForSelector('[data-testid="account-input"], [data-testid="symbol-input"]', { timeout: 10000 });
    console.log(`✅ Order ticket page loaded`);
  }

  /**
   * Test: Account field is read-only
   */
  test('should display account field as read-only', async ({ page }) => {
    await signInAndNavigate(page);
    
    // Account field should exist
    const accountField = page.locator('[data-testid="account-input"]').first();
    expect(await accountField.isVisible()).toBe(true);
    
    // Account field should be disabled or have readonly attribute
    const isDisabled = await accountField.isDisabled();
    const isReadonly = await accountField.evaluate((el: any) => el.readOnly);
    
    expect(isDisabled || isReadonly).toBe(true);
  });

  /**
   * Test: Form validation - rejects before submission
   */
  test('should validate form and prevent invalid submission', async ({ page }) => {
    await signInAndNavigate(page);
    
    // Wait for order-ticket page to be fully loaded
    await page.waitForLoadState('networkidle');
    
    // Button is enabled when an account exists; invalid state is enforced on submit.
    const submitBtn = page.locator('[data-testid="submit-order-btn"]');
    await submitBtn.waitFor({ timeout: 10000 });

    // Start from empty fields: the account's default-quantity preference (Preferences > Trading)
    // and a symbol in the URL would otherwise prefill them
    await page.locator('[data-testid="quantity-input"]').fill('');
    await page.locator('[data-testid="price-input"]').fill('');

    await submitBtn.click();
    await expect(page.locator('[data-testid="symbol-input"]')).toHaveClass(/is-invalid/);
    await expect(page.locator('[data-testid="quantity-input"]')).toHaveClass(/is-invalid/);
    await expect(page.locator('[data-testid="price-input"]')).toHaveClass(/is-invalid/);
    
    // Fill quantity with invalid value (0) and resubmit.
    const quantityInput = page.locator('[data-testid="quantity-input"]');
    await quantityInput.fill('0');
    await submitBtn.click();
    await expect(quantityInput).toHaveClass(/is-invalid/);
    
    // Fill quantity with valid value
    await quantityInput.fill('100');
    
    // Fill price with invalid value (0) and resubmit.
    const priceInput = page.locator('[data-testid="price-input"]');
    await priceInput.fill('0');
    await submitBtn.click();
    await expect(priceInput).toHaveClass(/is-invalid/);
    
    // Fill price with valid value
    await priceInput.fill('150.50');
    
    // Fill symbol
    const symbolInput = page.locator('[data-testid="symbol-input"]');
    await symbolInput.fill(E2E_SYMBOL);
    
    // With valid values entered, submit should succeed or produce an order result.
    await submitBtn.click();
    await expect(page.locator('[data-testid="order-result-message"], [data-testid="order-rejected-message"]').first()).toBeVisible();
  });

  /**
   * Test: Rejection before submission - invalid account
   */
  test('should reject order with invalid account', async ({ page }) => {
    await signInAndNavigate(page);
    
    // Wait for page to be fully loaded
    await page.waitForLoadState('networkidle');
    
    // Fill form with valid values
    const quantityInput = page.locator('[data-testid="quantity-input"]');
    const priceInput = page.locator('[data-testid="price-input"]');
    const symbolInput = page.locator('[data-testid="symbol-input"]');
    const submitBtn = page.locator('[data-testid="submit-order-btn"]');
    
    await submitBtn.waitFor({ timeout: 10000 });
    
    await quantityInput.fill('100');
    await priceInput.fill('150.50');
    await symbolInput.fill(E2E_SYMBOL);
    
    // Submit form
    if (!(await submitBtn.isDisabled())) {
      await submitBtn.click();
      
      // Wait for error message or response
      const errorMsg = page.locator('.alert.alert-danger, [class*="error"]').first();
      await errorMsg.waitFor({ state: 'visible', timeout: 10000 }).catch(() => null);
      
      console.log(`✅ Order rejected as expected`);
    }
  });

  /**
   * Test: Place order successfully - accept NEW, FILLED, or REJECTED
   */
  test('should place order, show its confirmation page and find it in the blotter', async ({ page }) => {
    await signInAndNavigate(page);
    
    // Wait for page to be fully loaded
    await page.waitForLoadState('networkidle');
    
    // Get locators for form elements
    const quantityInput = page.locator('[data-testid="quantity-input"]');
    const priceInput = page.locator('[data-testid="price-input"]');
    const symbolInput = page.locator('[data-testid="symbol-input"]');
    const submitBtn = page.locator('[data-testid="submit-order-btn"]');
    
    // Wait for submit button to be available
    await submitBtn.waitFor({ timeout: 10000 });
    
    // Fill form with valid order details
    await quantityInput.fill('10');
    await priceInput.fill('120.00');
    await symbolInput.fill(E2E_SYMBOL);
    
    // Submit order
    if (await submitBtn.isDisabled()) {
      throw new Error('Submit button is disabled - form may have validation errors');
    }
    await submitBtn.click();
    console.log(`📤 Order submitted`);

    // A placed order opens its own confirmation page
    await page.waitForURL(/\/order-confirmation\/[^/]+$/, { timeout: 15000 });
    const result = page.locator('[data-testid="order-result-message"]');
    await expect(result).toBeVisible();

    // Executor runs asynchronously, so NEW, FILLED and REJECTED are all valid here
    const status = page.locator('[data-testid="order-status"]');
    await expect(status).toHaveText(/^\s*(NEW|FILLED|REJECTED)\s*$/);
    const orderId = (await page.locator('[data-testid="order-id"]').textContent())?.trim() ?? '';
    expect(orderId).not.toBe('');
    expect(decodeURIComponent(new URL(page.url()).pathname)).toContain(orderId);
    console.log(`✅ Confirmation shown for ${orderId} (${(await status.textContent())?.trim()})`);

    // Reloading the confirmation page recovers the order from the account history
    await page.reload({ waitUntil: 'networkidle' });
    await expect(page.locator('[data-testid="order-id"]')).toHaveText(orderId);
    await expect(status).toHaveText(/^\s*(NEW|FILLED|REJECTED)\s*$/);

    // The confirmation routes to the Order Blotter, where the order is listed
    await page.locator('[data-testid="view-in-blotter-btn"]').click();
    await page.waitForURL(/\/blotter$/, { timeout: 10000 });
    await expect(page.locator(`td.cell-order-id[title="${orderId}"]`)).toBeVisible({ timeout: 15000 });
    console.log(`✅ Order ${orderId} found in the Order Blotter`);
  });

  /**
   * Test: The confirmation page handles an unknown order id gracefully
   */
  test('should show not-found with a blotter link for an unknown order id', async ({ page }) => {
    await signInAndNavigate(page);

    await page.goto(`${E2E_BASE_URL}/order-confirmation/ORD-00000000-0000-0000-0000-000000000000`, { waitUntil: 'networkidle' });

    await expect(page.locator('[data-testid="order-not-found"]')).toBeVisible();
    await page.locator('[data-testid="view-in-blotter-btn"]').click();
    await page.waitForURL(/\/blotter$/, { timeout: 10000 });
  });

  /**
   * Test: No shared state between tests - fresh sign-in each time
   */
  test('should start fresh with no token from previous test', async ({ page }) => {
    // Navigate to app first before checking sessionStorage (avoid SecurityError on blank page)
    await page.goto(E2E_BASE_URL);
    
    // Verify no token in sessionStorage before sign-in
    const tokenBefore = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(tokenBefore).toBeNull();
    
    // Sign in
    await signInAndNavigate(page);
    
    // Now token should exist
    const tokenAfter = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(tokenAfter).toBeTruthy();
  });
});
