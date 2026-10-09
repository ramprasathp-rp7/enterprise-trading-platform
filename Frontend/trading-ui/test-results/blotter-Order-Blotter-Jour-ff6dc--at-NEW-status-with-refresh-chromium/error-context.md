# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: blotter.spec.ts >> Order Blotter Journey >> should handle orders at NEW status with refresh
- Location: e2e\blotter.spec.ts:164:7

# Error details

```
Error: Sign-in failed for Raahul: The username or password is incorrectAUTH-401
```

# Page snapshot

```yaml
- generic [ref=e4]:
  - complementary [aria-hidden] [ref=e5]:
    - generic [ref=e6]: Enterprise Trading Platform
    - generic [ref=e10]:
      - heading [level=2] [ref=e11]: Trade with clarity and control.
      - paragraph [ref=e12]: Place orders, monitor positions and manage funds from one secure workspace.
      - list [ref=e13]:
        - listitem [ref=e14]: Real-time order status and blotter
        - listitem [ref=e16]: Portfolio allocation at a glance
        - listitem [ref=e18]: Token-secured sessions
    - paragraph [ref=e20]: Institutional Markets
  - main [ref=e21]:
    - button "Switch to dark mode" [ref=e22] [cursor=pointer]: Dark mode
    - generic [ref=e24]:
      - generic [ref=e25]:
        - paragraph [ref=e26]: Secure Sign In
        - heading "Sign In — Enterprise Trading Platform" [level=1] [ref=e28]
        - paragraph [ref=e29]: Use your workspace credentials to continue.
      - alert [ref=e31]:
        - generic [ref=e32]: The username or password is incorrect
        - generic "Error code" [ref=e33]: AUTH-401
      - generic [ref=e35]:
        - generic [ref=e36]:
          - generic [ref=e37]: Username
          - textbox "Username" [ref=e38]:
            - /placeholder: Enter your username
            - text: Raahul
        - generic [ref=e39]:
          - generic [ref=e40]: Password
          - textbox "Password" [ref=e41]:
            - /placeholder: Enter your password
            - text: Raahul160205
        - button "Sign In" [ref=e42] [cursor=pointer]
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test';
  2   | 
  3   | /**
  4   |  * E2E Journey: Order Blotter (Bonus)
  5   |  * 
  6   |  * Requirements:
  7   |  * - View order history with newest first
  8   |  * - Status badges: NEW, FILLED, REJECTED, CANCELLED
  9   |  * - Orders at NEW status: screen refreshes to show updates
  10  |  * - Blotter shows both working and finished orders
  11  |  */
  12  | 
  13  | // Environment variables
  14  | const E2E_BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:4200';
  15  | const E2E_AUTH_API = process.env.E2E_AUTH_API || 'http://localhost:3000';
  16  | const E2E_USERNAME = process.env.E2E_USERNAME || 'test_user_1';
  17  | const E2E_PASSWORD = process.env.E2E_PASSWORD || 'Test@Password1';
  18  | const E2E_ACCOUNT_ID = process.env.E2E_ACCOUNT_ID || '1';
  19  | 
  20  | test.setTimeout(60000);
  21  | 
  22  | test.describe('Order Blotter Journey', () => {
  23  |   /**
  24  |    * Setup: Register test user
  25  |    */
  26  |   test.beforeAll(async () => {
  27  |     try {
  28  |       const res = await fetch(`${E2E_AUTH_API}/auth/register`, {
  29  |         method: 'POST',
  30  |         headers: { 'Content-Type': 'application/json' },
  31  |         body: JSON.stringify({
  32  |           username: E2E_USERNAME,
  33  |           password: E2E_PASSWORD,
  34  |           accountId: E2E_ACCOUNT_ID,
  35  |           roles: ['CUSTOMER'],
  36  |         }),
  37  |       });
  38  |       
  39  |       if (res.status === 201 || res.status === 409) {
  40  |         console.log(`✅ Test user ready for blotter tests`);
  41  |       }
  42  |     } catch (err) {
  43  |       console.error(`❌ Setup error:`, err);
  44  |     }
  45  |   });
  46  | 
  47  |   /** Orders table, or the empty state when the account has no orders */
  48  |   const ORDER_LIST = '.transactions-table, .empty-state';
  49  |   const ORDER_ROWS = 'tr.transaction-row';
  50  |   const STATUSES = ['NEW', 'FILLED', 'REJECTED', 'CANCELLED'];
  51  | 
  52  |   /** GET /api/v1/accounts/{id}/orders, the request the blotter loads its history from */
  53  |   const isOrderHistoryResponse = (r: any) =>
  54  |     /\/api\/v1\/accounts\/\d+\/orders(\?|$)/.test(r.url()) && r.request().method() === 'GET';
  55  | 
  56  |   /**
  57  |    * Helper: Sign in and navigate to blotter.
  58  |    * Returns the order history the blotter loaded (newest first, per the contract).
  59  |    */
  60  |   async function signInAndViewBlotter(page: any): Promise<{ orderId: string; createdOn: string; status: string }[]> {
  61  |     console.log(`\n🔐 Signing in as: ${E2E_USERNAME}`);
  62  |     await page.goto(`${E2E_BASE_URL}/sign-in`, { waitUntil: 'networkidle' });
  63  |     
  64  |     // Fill form
  65  |     const usernameInput = page.locator('[data-testid="username-input"]');
  66  |     const passwordInput = page.locator('[data-testid="password-input"]');
  67  |     const submitBtn = page.locator('[data-testid="submit-btn"]');
  68  |     
  69  |     await usernameInput.fill(E2E_USERNAME);
  70  |     await passwordInput.fill(E2E_PASSWORD);
  71  |     
  72  |     console.log(`📝 Form filled, submitting...`);
  73  |     await submitBtn.click();
  74  |     
  75  |     // Wait for redirect to dashboard
  76  |     try {
  77  |       await page.waitForURL(url => url.pathname.includes('/dashboard'), { timeout: 10000 });
  78  |       console.log(`✅ Redirected to /dashboard`);
  79  |     } catch (e) {
  80  |       console.log(`⏳ No redirect, checking token...`);
  81  |     }
  82  |     
  83  |     // Check for token
  84  |     const token = await page.evaluate(() => sessionStorage.getItem('access_token')).catch(() => null);
  85  |     
  86  |     if (!token) {
  87  |       // Check for error message
  88  |       const errorMsg = await page.locator('.alert.alert-danger, [class*="error"]').first().textContent().catch(() => 'Unknown error');
  89  |       console.error(`❌ Sign-in failed: ${errorMsg}`);
> 90  |       throw new Error(`Sign-in failed for ${E2E_USERNAME}: ${errorMsg}`);
      |             ^ Error: Sign-in failed for Raahul: The username or password is incorrectAUTH-401
  91  |     }
  92  |     
  93  |     console.log(`✅ Token acquired`);
  94  |     
  95  |     // Navigate to blotter, capturing the history it loads
  96  |     console.log(`📊 Navigating to blotter...`);
  97  |     const historyResponse = page.waitForResponse(isOrderHistoryResponse, { timeout: 10000 });
  98  |     await page.goto(`${E2E_BASE_URL}/blotter`);
  99  |     const response = await historyResponse;
  100 |     expect(response.ok()).toBe(true);
  101 |     await expect(page.locator(ORDER_LIST).first()).toBeVisible({ timeout: 10000 });
  102 |     console.log(`✅ Blotter page loaded`);
  103 |     return response.json();
  104 |   }
  105 | 
  106 |   /**
  107 |    * Test: Blotter loads with order history
  108 |    */
  109 |   test('should display order history on blotter', async ({ page }) => {
  110 |     const orders = await signInAndViewBlotter(page);
  111 |     
  112 |     expect(page.url()).toContain('/blotter');
  113 |     
  114 |     // One row per order, or the empty state when there are none
  115 |     if (orders.length > 0) {
  116 |       await expect(page.locator(ORDER_ROWS)).toHaveCount(orders.length);
  117 |     } else {
  118 |       await expect(page.locator('.empty-state')).toBeVisible();
  119 |     }
  120 |   });
  121 | 
  122 |   /**
  123 |    * Test: Status badges visible (NEW, FILLED, REJECTED, CANCELLED)
  124 |    */
  125 |   test('should display status badges for orders', async ({ page }) => {
  126 |     const orders = await signInAndViewBlotter(page);
  127 |     test.skip(orders.length === 0, 'Account has no orders to show badges for');
  128 |     
  129 |     // Every row has exactly one badge, showing that order's status
  130 |     const rows = page.locator(ORDER_ROWS);
  131 |     const rowCount = await rows.count();
  132 |     for (let i = 0; i < rowCount; i++) {
  133 |       const badge = rows.nth(i).locator('.status-badge');
  134 |       await expect(badge).toHaveCount(1);
  135 |       const status = (await badge.getAttribute('aria-label')) ?? '';
  136 |       expect(STATUSES).toContain(status);
  137 |       await expect(badge).toHaveText(status);
  138 |     }
  139 |   });
  140 | 
  141 |   /**
  142 |    * Test: Newest orders first (most recent at top)
  143 |    */
  144 |   test('should display orders with newest first', async ({ page }) => {
  145 |     const orders = await signInAndViewBlotter(page);
  146 |     test.skip(orders.length < 2, 'Need at least two orders to check ordering');
  147 |     
  148 |     // Rows carry the full order id in their title; look up each one's createdOn
  149 |     const createdOn = new Map(orders.map(o => [o.orderId, Date.parse(o.createdOn)]));
  150 |     const rowIds = await page.locator(`${ORDER_ROWS} td.cell-order-id`).evaluateAll(
  151 |       (cells: Element[]) => cells.map(c => c.getAttribute('title') ?? ''),
  152 |     );
  153 |     const rowTimes = rowIds.map(id => createdOn.get(id));
  154 |     
  155 |     expect(rowTimes).not.toContain(undefined);
  156 |     for (let i = 1; i < rowTimes.length; i++) {
  157 |       expect(rowTimes[i - 1]!).toBeGreaterThanOrEqual(rowTimes[i]!);
  158 |     }
  159 |   });
  160 | 
  161 |   /**
  162 |    * Test: Orders at NEW status show refresh capability
  163 |    */
  164 |   test('should handle orders at NEW status with refresh', async ({ page }) => {
  165 |     const orders = await signInAndViewBlotter(page);
  166 |     test.skip(!orders.some(o => o.status === 'NEW'), 'No NEW orders to test refresh');
  167 |     
  168 |     // Refresh re-requests the history and the list re-renders
  169 |     const refreshBtn = page.locator('[data-testid="refresh-btn"]');
  170 |     await expect(refreshBtn).toBeEnabled();
  171 |     const refreshed = page.waitForResponse(isOrderHistoryResponse, { timeout: 10000 });
  172 |     await refreshBtn.click();
  173 |     const response = await refreshed;
  174 |     
  175 |     expect(response.ok()).toBe(true);
  176 |     const latest: unknown[] = await response.json();
  177 |     await expect(refreshBtn).toBeEnabled();
  178 |     await expect(page.locator(ORDER_ROWS)).toHaveCount(latest.length);
  179 |   });
  180 | 
  181 |   /**
  182 |    * Test: Rejected orders included in blotter
  183 |    */
  184 |   test('should include rejected orders in history', async ({ page }) => {
  185 |     const orders = await signInAndViewBlotter(page);
  186 |     const rejected = orders.filter(o => o.status === 'REJECTED').length;
  187 |     test.skip(rejected === 0, 'No rejected orders in history (may not have any yet)');
  188 |     
  189 |     await expect(page.locator(`${ORDER_ROWS} .status-badge[aria-label="REJECTED"]`)).toHaveCount(rejected);
  190 |   });
```