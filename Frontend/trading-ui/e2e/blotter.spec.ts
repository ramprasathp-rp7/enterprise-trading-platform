import { test, expect } from '@playwright/test';

/**
 * E2E Journey: Order Blotter (Bonus)
 * 
 * Requirements:
 * - View order history with newest first
 * - Status badges: NEW, FILLED, REJECTED, CANCELLED
 * - Orders at NEW status: screen refreshes to show updates
 * - Blotter shows both working and finished orders
 */

// Environment variables
const E2E_BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:4200';
const E2E_AUTH_API = process.env.E2E_AUTH_API || 'http://localhost:3000';
const E2E_USERNAME = process.env.E2E_USERNAME || 'test_user_1';
const E2E_PASSWORD = process.env.E2E_PASSWORD || 'Test@Password1';
const E2E_ACCOUNT_ID = process.env.E2E_ACCOUNT_ID || '1';

test.setTimeout(60000);

test.describe('Order Blotter Journey', () => {
  /**
   * Setup: Register test user
   */
  test.beforeAll(async () => {
    try {
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
        console.log(`✅ Test user ready for blotter tests`);
      }
    } catch (err) {
      console.error(`❌ Setup error:`, err);
    }
  });

  /** Orders table, or the empty state when the account has no orders */
  const ORDER_LIST = '.transactions-table, .empty-state';
  const ORDER_ROWS = 'tr.transaction-row';
  const STATUSES = ['NEW', 'FILLED', 'REJECTED', 'CANCELLED'];

  /** GET /api/v1/accounts/{id}/orders, the request the blotter loads its history from */
  const ORDER_HISTORY_URL = /\/api\/v1\/accounts\/\d+\/orders(\?|$)/;

  /**
   * Captures the next order-history response. Playwright fetches it itself and hands it on to the page,
   * so the body is read while it exists: WebKit drops response bodies quickly, and the blotter keeps
   * re-requesting the history while an order is NEW, so reading it later through waitForResponse fails
   * there with "Missing resource for given requestId".
   */
  async function nextOrderHistory(page: any): Promise<{ next: Promise<{ ok: boolean; orders: any[] }> }> {
    let resolve!: (value: { ok: boolean; orders: any[] }) => void;
    const result = new Promise<{ ok: boolean; orders: any[] }>((r) => (resolve = r));
    let captured = false;
    await page.route(ORDER_HISTORY_URL, async (route: any) => {
      // The CORS preflight (OPTIONS) and later polls go straight to the server
      if (captured || route.request().method() !== 'GET') {
        return route.fallback();
      }
      captured = true;
      const response = await route.fetch();
      const orders = response.ok() ? await response.json() : [];
      await route.fulfill({ response });
      resolve({ ok: response.ok(), orders });
    });
    // Wrapped: an async function returning a bare promise would wait for it
    return { next: result };
  }

  /**
   * Helper: Sign in and navigate to blotter.
   * Returns the order history the blotter loaded (newest first, per the contract).
   */
  async function signInAndViewBlotter(page: any): Promise<{ orderId: string; createdOn: string; status: string }[]> {
    console.log(`\n🔐 Signing in as: ${E2E_USERNAME}`);
    await page.goto(`${E2E_BASE_URL}/sign-in`, { waitUntil: 'networkidle' });
    
    // Fill form
    const usernameInput = page.locator('[data-testid="username-input"]');
    const passwordInput = page.locator('[data-testid="password-input"]');
    const submitBtn = page.locator('[data-testid="submit-btn"]');
    
    await usernameInput.fill(E2E_USERNAME);
    await passwordInput.fill(E2E_PASSWORD);
    
    console.log(`📝 Form filled, submitting...`);
    await submitBtn.click();
    
    // Wait for redirect to dashboard
    try {
      await page.waitForURL(url => url.pathname.includes('/dashboard'), { timeout: 10000 });
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
    
    // Navigate to blotter, capturing the history it loads
    console.log(`📊 Navigating to blotter...`);
    const history = await nextOrderHistory(page);
    await page.goto(`${E2E_BASE_URL}/blotter`);
    const { ok, orders } = await history.next;
    expect(ok).toBe(true);
    await expect(page.locator(ORDER_LIST).first()).toBeVisible({ timeout: 10000 });
    console.log(`✅ Blotter page loaded`);
    return orders;
  }

  /**
   * Test: Blotter loads with order history
   */
  test('should display order history on blotter', async ({ page }) => {
    const orders = await signInAndViewBlotter(page);
    
    expect(page.url()).toContain('/blotter');
    
    // One row per order, or the empty state when there are none
    if (orders.length > 0) {
      await expect(page.locator(ORDER_ROWS)).toHaveCount(orders.length);
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  /**
   * Test: Status badges visible (NEW, FILLED, REJECTED, CANCELLED)
   */
  test('should display status badges for orders', async ({ page }) => {
    const orders = await signInAndViewBlotter(page);
    test.skip(orders.length === 0, 'Account has no orders to show badges for');
    
    // Every row has exactly one badge, showing that order's status
    const rows = page.locator(ORDER_ROWS);
    const rowCount = await rows.count();
    for (let i = 0; i < rowCount; i++) {
      const badge = rows.nth(i).locator('.status-badge');
      await expect(badge).toHaveCount(1);
      const status = (await badge.getAttribute('aria-label')) ?? '';
      expect(STATUSES).toContain(status);
      await expect(badge).toHaveText(status);
    }
  });

  /**
   * Test: Newest orders first (most recent at top)
   */
  test('should display orders with newest first', async ({ page }) => {
    const orders = await signInAndViewBlotter(page);
    test.skip(orders.length < 2, 'Need at least two orders to check ordering');
    
    // Rows carry the full order id in their title; look up each one's createdOn
    const createdOn = new Map(orders.map(o => [o.orderId, Date.parse(o.createdOn)]));
    const rowIds = await page.locator(`${ORDER_ROWS} td.cell-order-id`).evaluateAll(
      (cells: Element[]) => cells.map(c => c.getAttribute('title') ?? ''),
    );
    const rowTimes = rowIds.map(id => createdOn.get(id));
    
    expect(rowTimes).not.toContain(undefined);
    for (let i = 1; i < rowTimes.length; i++) {
      expect(rowTimes[i - 1]!).toBeGreaterThanOrEqual(rowTimes[i]!);
    }
  });

  /**
   * Test: Orders at NEW status show refresh capability
   */
  test('should handle orders at NEW status with refresh', async ({ page }) => {
    const orders = await signInAndViewBlotter(page);
    test.skip(!orders.some(o => o.status === 'NEW'), 'No NEW orders to test refresh');
    
    // Refresh re-requests the history and the list re-renders
    const refreshBtn = page.locator('[data-testid="refresh-btn"]');
    await expect(refreshBtn).toBeEnabled();
    const refreshed = await nextOrderHistory(page);
    await refreshBtn.click();
    const { ok, orders: latest } = await refreshed.next;

    expect(ok).toBe(true);
    await expect(refreshBtn).toBeEnabled();
    await expect(page.locator(ORDER_ROWS)).toHaveCount(latest.length);
  });

  /**
   * Test: Rejected orders included in blotter
   */
  test('should include rejected orders in history', async ({ page }) => {
    const orders = await signInAndViewBlotter(page);
    const rejected = orders.filter(o => o.status === 'REJECTED').length;
    test.skip(rejected === 0, 'No rejected orders in history (may not have any yet)');
    
    await expect(page.locator(`${ORDER_ROWS} .status-badge[aria-label="REJECTED"]`)).toHaveCount(rejected);
  });

  /**
   * Test: No shared state - fresh session each time
   */
  test('should start with fresh session for each test', async ({ page }) => {
    // Navigate to app first before checking sessionStorage (avoid SecurityError on blank page)
    await page.goto(E2E_BASE_URL);
    
    // Verify no token before sign-in
    const tokenBefore = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(tokenBefore).toBeNull();
    
    // Sign in
    await signInAndViewBlotter(page);
    
    // Verify token now exists
    const tokenAfter = await page.evaluate(() => sessionStorage.getItem('access_token'));
    expect(tokenAfter).toBeTruthy();
  });
});
