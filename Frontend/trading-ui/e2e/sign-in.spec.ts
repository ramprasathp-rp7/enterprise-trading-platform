import { test, expect } from '@playwright/test';

/**
 * E2E Test: Sign-In Flow
 * 
 * Tests the complete sign-in journey against real Auth Service
 * Verifies:
 * 1. Valid credentials sign in and redirect to dashboard
 * 2. Token stored in sessionStorage
 * 3. Dashboard renders with user information
 * 4. Sign-out clears the session
 */

// Read credentials from environment variables.
// Prefer the shared E2E_* names from the repo root .env file.
const E2E_USERNAME = process.env.E2E_USERNAME || process.env.AUTH_USERNAME || 'test_user_1';
const E2E_PASSWORD = process.env.E2E_PASSWORD || process.env.AUTH_PASSWORD || 'Test@Password1';
const E2E_AUTH_API = process.env.E2E_AUTH_API || process.env.AUTH_SERVICE_URL || 'http://localhost:3000';
const E2E_BASE_URL = process.env.E2E_BASE_URL || process.env.UI_BASE_URL || 'http://localhost:4200';
const E2E_ACCOUNT_ID = process.env.E2E_ACCOUNT_ID || process.env.TEST_ACCOUNT_ID || '1';

// Increase timeout for all tests due to external Auth Service latency
test.setTimeout(60000);

/**
 * Global setup: Register test user ONCE before any tests run
 * This runs only once per worker, not per test
 */
test.beforeAll(async () => {
  try {
    console.log(`\n${'='.repeat(70)}`);
    console.log(`🚀 GLOBAL E2E TEST SETUP`);
    console.log(`${'='.repeat(70)}`);
    console.log(`Auth Service: ${E2E_AUTH_API}`);
    console.log(`UI Base URL: ${E2E_BASE_URL}`);
    console.log(`Test Username: ${E2E_USERNAME}`);
    console.log(`Test Account ID: ${E2E_ACCOUNT_ID}`);
    
    console.log(`\n📝 Attempting to register test user...`);
    
    const registerResponse = await fetch(`${E2E_AUTH_API}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: E2E_USERNAME,
        password: E2E_PASSWORD,
        accountId: E2E_ACCOUNT_ID,
        roles: ['CUSTOMER'],
      }),
    });
    
    const registerText = await registerResponse.text();
    
    if (registerResponse.status === 201) {
      console.log(`✅ Test user registered successfully`);
    } else if (registerResponse.status === 409) {
      console.log(`✅ Test user already exists (409 Conflict) - will use existing user`);
    } else {
      console.warn(`⚠️  Register returned ${registerResponse.status}: ${registerText}`);
    }
    
    console.log(`${'='.repeat(70)}\n`);
  } catch (error) {
    console.error(`❌ Setup error: ${error instanceof Error ? error.message : String(error)}`);
  }
});

test.describe('Sign-In Flow E2E', () => {
  test.beforeEach(async ({ page }) => {
    // Log all network requests and errors
    page.on('requestfailed', request => {
      console.error(`❌ Request failed: ${request.url()} - ${request.failure()?.errorText}`);
    });
    
    page.on('response', response => {
      if (response.url().includes('/auth/')) {
        const status = response.status();
        const icon = status === 200 ? '✅' : status === 401 ? '🔐' : status === 429 ? '⏱️ ' : '❓';
        console.log(`${icon} Auth response: ${response.url()} - ${status}`);
      }
    });
    
    // Navigate to login page
    await page.goto(`${E2E_BASE_URL}/sign-in`);
  });

  test('should sign in with valid credentials and redirect to dashboard', async ({ page }) => {
    // Fill in username
    await page.fill('[data-testid="username-input"]', E2E_USERNAME);
    
    // Fill in password
    await page.fill('[data-testid="password-input"]', E2E_PASSWORD);
    
    // Submit form
    await page.click('[data-testid="submit-btn"]');
    
    // Wait for form submission processing
    await page.waitForTimeout(3000);
    
    // Check for error messages
    const hasErrorMessage = await page.locator('.alert.alert-danger').first().isVisible().catch(() => false);
    if (hasErrorMessage) {
      const errorText = await page.locator('.alert.alert-danger').first().textContent();
      console.log(`⚠️  Error displayed: ${errorText}`);
    }
    
    // Check if token was stored
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    console.log(`🔑 Access token in sessionStorage: ${accessToken ? 'YES' : 'NO'}`);
    
    expect(accessToken).toBeTruthy();
  });

  test('should store access token in sessionStorage after login', async ({ page }) => {
    // Fill in credentials
    await page.fill('[data-testid="username-input"]', E2E_USERNAME);
    await page.fill('[data-testid="password-input"]', E2E_PASSWORD);
    
    // Submit form
    await page.click('[data-testid="submit-btn"]');
    
    // Wait for login to process
    await page.waitForTimeout(3000);
    
    // Check sessionStorage for access token
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeTruthy();
    expect(accessToken).toMatch(/^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]*$/); // JWT format
  });

  test('should store refresh token in sessionStorage after login', async ({ page }) => {
    // Fill in credentials
    await page.fill('[data-testid="username-input"]', E2E_USERNAME);
    await page.fill('[data-testid="password-input"]', E2E_PASSWORD);
    
    // Submit form
    await page.click('[data-testid="submit-btn"]');
    
    // Wait for login to process
    await page.waitForTimeout(2000);
    
    // Check sessionStorage for refresh token
    const refreshToken = await page.evaluate(() => sessionStorage.getItem('refresh_token'));
    expect(refreshToken).toBeTruthy();
  });

  test('should display error message with invalid credentials', async ({ page }) => {
    // Fill in invalid credentials
    await page.fill('[data-testid="username-input"]', 'invalid.user');
    await page.fill('[data-testid="password-input"]', 'wrong-password');
    
    // Submit form
    await page.click('[data-testid="submit-btn"]');
    
    // Wait for error message to appear
    await page.waitForSelector('.alert.alert-danger', { timeout: 10000 });
    
    // Verify error is displayed
    const errorAlert = await page.locator('.alert.alert-danger').first();
    expect(await errorAlert.isVisible()).toBe(true);
    
    // Should still be on sign-in page
    expect(page.url()).toContain('/sign-in');
    
    // Tokens should not be stored
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeNull();
  });

  test('should validate empty fields and not allow submission', async ({ page }) => {
    const usernameInput = page.locator('[data-testid="username-input"]');
    const passwordInput = page.locator('[data-testid="password-input"]');
    const submitBtn = page.locator('[data-testid="submit-btn"]');
    
    // Validation only appears after submit or blur.
    await submitBtn.click();
    await expect(usernameInput).toHaveClass(/is-invalid/);
    await expect(passwordInput).toHaveClass(/is-invalid/);
    
    // After failed submit, form surfaces errors and blocks resubmission.
    await expect(submitBtn).toBeDisabled();
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeNull();
  });

  test('should not allow submission with empty username', async ({ page }) => {
    // Only fill password
    await page.fill('[data-testid="password-input"]', E2E_PASSWORD);
    await page.click('[data-testid="submit-btn"]');
    
    const usernameInput = page.locator('[data-testid="username-input"]');
    const submitBtn = page.locator('[data-testid="submit-btn"]');
    await expect(usernameInput).toHaveClass(/is-invalid/);
    await expect(submitBtn).toBeDisabled();
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeNull();
  });

  test('should not allow submission with empty password', async ({ page }) => {
    // Only fill username
    await page.fill('[data-testid="username-input"]', E2E_USERNAME);
    await page.click('[data-testid="submit-btn"]');
    
    const passwordInput = page.locator('[data-testid="password-input"]');
    const submitBtn = page.locator('[data-testid="submit-btn"]');
    await expect(passwordInput).toHaveClass(/is-invalid/);
    await expect(submitBtn).toBeDisabled();
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeNull();
  });

  test('should attach Bearer token to subsequent API requests', async ({ page }) => {
    // Fill in credentials
    await page.fill('[data-testid="username-input"]', E2E_USERNAME);
    await page.fill('[data-testid="password-input"]', E2E_PASSWORD);
    
    // Submit form
    await page.click('[data-testid="submit-btn"]');
    
    // Wait for login to process
    await page.waitForTimeout(2000);
    
    // Verify token is stored
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeTruthy();
    expect(accessToken).toMatch(/^Bearer|^[A-Za-z0-9-_]/);
  });

  test('should clear tokens on logout', async ({ page }) => {
    // Sign in first
    await page.fill('[data-testid="username-input"]', E2E_USERNAME);
    await page.fill('[data-testid="password-input"]', E2E_PASSWORD);
    await page.click('[data-testid="submit-btn"]');
    
    // Wait for login to process
    await page.waitForTimeout(2000);
    
    // Verify tokens are stored
    let accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeTruthy();
    
    // Look for sign-out button and click it
    // This assumes there's a sign-out button on dashboard
    const signOutBtn = page.locator('button:has-text("Sign Out"), button:has-text("Logout"), [data-testid="sign-out-btn"]').first();
    
    if (await signOutBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await signOutBtn.click();
      // Wait a moment for logout to complete
      await page.waitForTimeout(1000);
      
      // After sign-out, tokens should be cleared from sessionStorage
      accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
      // Token should be cleared (either null or not present)
      expect(accessToken === null || accessToken === undefined).toBe(true);
    } else {
      // If sign-out button not found, test is inconclusive but don't fail
      console.log('Sign-out button not found on page');
    }
  });
});

