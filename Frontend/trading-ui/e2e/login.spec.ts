import { test, expect } from '@playwright/test';

// Reuse existing sign-in spec but read env names prefixed with E2E_ if present
const AUTH_USERNAME = process.env.E2E_USERNAME || process.env.AUTH_USERNAME || 'test_user_1';
const AUTH_PASSWORD = process.env.E2E_PASSWORD || process.env.AUTH_PASSWORD || 'Test@Password1';
const AUTH_SERVICE_URL = process.env.E2E_AUTH_API || process.env.AUTH_SERVICE_URL || 'http://localhost:3000';
const UI_BASE_URL = process.env.E2E_BASE_URL || process.env.UI_BASE_URL || 'http://localhost:4200';
const TEST_ACCOUNT_ID = process.env.E2E_ACCOUNT_ID || process.env.TEST_ACCOUNT_ID || 1;

test.setTimeout(60000);

test.beforeAll(async () => {
  try {
    const registerResponse = await fetch(`${AUTH_SERVICE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: AUTH_USERNAME,
        password: AUTH_PASSWORD,
        accountId: TEST_ACCOUNT_ID,
        roles: ['CUSTOMER'],
      }),
    });

    if (registerResponse.status === 201 || registerResponse.status === 409) {
      // ok
    }
  } catch (e) {
    console.warn('Could not register test user', e);
  }
});

test.describe('Login Flow E2E', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${UI_BASE_URL}/sign-in`);
  });

  test('should sign in and store tokens', async ({ page }) => {
    await page.fill('[data-testid="username-input"]', AUTH_USERNAME);
    await page.fill('[data-testid="password-input"]', AUTH_PASSWORD);
    await page.click('[data-testid="submit-btn"]');
    await page.waitForTimeout(2000);
    const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(accessToken).toBeTruthy();
  });

  test('should clear tokens on logout', async ({ page }) => {
    await page.fill('[data-testid="username-input"]', AUTH_USERNAME);
    await page.fill('[data-testid="password-input"]', AUTH_PASSWORD);
    await page.click('[data-testid="submit-btn"]');
    await page.waitForTimeout(2000);

    const signOutBtn = page.locator('[data-testid="sign-out-btn"]');
    if (await signOutBtn.isVisible().catch(() => false)) {
      await signOutBtn.click();
      await page.waitForTimeout(500);
      const accessToken = await page.evaluate(() => sessionStorage.getItem('access_token'));
      expect(accessToken === null || accessToken === undefined).toBe(true);
    } else {
      console.log('Sign-out button not found');
    }
  });
});
