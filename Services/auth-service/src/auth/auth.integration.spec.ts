import * as jwt from 'jsonwebtoken';
import { beforeAll, describe, expect, it } from '@jest/globals';
import { loadPlatformConfig, toOrigin } from '../config/platform-config';
// Integration tests require external services. Run only when RUN_INTEGRATION_TESTS=true
// Force-enable integration tests when this file is executed.
// NOTE: This will attempt network calls to external services during test runs.
process.env.RUN_INTEGRATION_TESTS = 'true';
if (process.env.RUN_INTEGRATION_TESTS === 'true') {
describe('Auth Service Integration with Trade REST API', () => {
  const platform = loadPlatformConfig();
  const AUTH_SERVICE_URL = toOrigin(platform.services?.auth) ?? 'http://localhost:3000';
  const TRADE_API_URL = toOrigin(platform.services?.order) ?? 'http://localhost:8081';
  
  const JWT_SECRET = process.env.JWT_SECRET || 'test-shared-secret-32-bytes-minimum-key';
  const JWT_ISSUER = process.env.JWT_ISSUER || 'auth-service';
  
  let accessToken: string;
  let testUsername: string;
  let testPassword: string;
  let testAccountId: number;
  
  /**
   * Setup: a user to test with. The platform allows one user per trading account and only knows the
   * accounts in auth.known_account_ids, so the credentials are stable rather than new on every run:
   * the first run registers them, later runs get 409 and simply log in again.
   *
   *   AUTH_IT_ACCOUNT_ID                 account to register the test user on (default 6)
   *   AUTH_IT_USERNAME, AUTH_IT_PASSWORD use an existing customer instead of registering one
   */
  beforeAll(async () => {
    const useExisting = !!(process.env.AUTH_IT_USERNAME && process.env.AUTH_IT_PASSWORD);
    testAccountId = Number(process.env.AUTH_IT_ACCOUNT_ID ?? 6);
    testUsername = process.env.AUTH_IT_USERNAME ?? `it_user_account_${testAccountId}`;
    testPassword = process.env.AUTH_IT_PASSWORD ?? `it-pass-account-${testAccountId}`;

    if (!useExisting) {
      const registerResponse = await fetch(`${AUTH_SERVICE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: testUsername, password: testPassword, accountId: testAccountId }),
      });
      if (registerResponse.status === 201) {
        console.log(`Test user registered: ${testUsername} on account ${testAccountId}`);
      } else if (registerResponse.status !== 409) {
        // 422: the account is not a known trading account
        throw new Error(`Register failed: ${registerResponse.status} ${await registerResponse.text()}. ` +
          'Set AUTH_IT_ACCOUNT_ID to an account in auth.known_account_ids.');
      }
    }

    // Check the credentials work, and test against the account the token is actually for
    const loginResponse = await fetch(`${AUTH_SERVICE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: testUsername, password: testPassword }),
    });
    if (loginResponse.status !== 200) {
      throw new Error(
        `Cannot log in as ${testUsername} (${loginResponse.status}). Account ${testAccountId} probably already ` +
        'belongs to another user: set AUTH_IT_ACCOUNT_ID to a free known account, or AUTH_IT_USERNAME and ' +
        'AUTH_IT_PASSWORD to an existing customer.',
      );
    }
    const { accessToken: token } = (await loginResponse.json()) as { accessToken: string };
    testAccountId = (jwt.decode(token) as { accountId: number }).accountId;
  });

  describe('Happy Path: Token from Auth Service accepted by Trade API', () => {
    /**
     * Test: Login to auth service and get a valid token
     */
    it('should obtain access token from auth service login', async () => {
      const loginResponse = await fetch(`${AUTH_SERVICE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: testUsername,
          password: testPassword,
        }),
      });

      expect(loginResponse.status).toBe(200);
      const loginData = (await loginResponse.json()) as Record<string, unknown>;
      expect(loginData).toHaveProperty('accessToken');
      expect(loginData).toHaveProperty('refreshToken');
      expect(loginData).toHaveProperty('expiresIn');

      // Store token for subsequent tests
      accessToken = loginData.accessToken as string;
      
      // Verify token structure
      const decoded: any = jwt.decode(accessToken);
      expect(decoded).toBeDefined();
      expect(decoded.sub).toBeDefined(); // User ID
      expect(decoded.accountId).toBe(testAccountId);
      expect(decoded.roles).toContain('CUSTOMER');
      expect(decoded.iss).toBe(JWT_ISSUER);
    });

    /**
     * Test: Trade API accepts valid token from auth service
     * Demonstrates zero-change integration: Trade API verifies token by signature
     */
    it('should accept valid token from auth service in trade api protected route', async () => {
      // Ensure we have a token from login
      if (!accessToken) {
        const loginResponse = await fetch(`${AUTH_SERVICE_URL}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: testUsername,
            password: testPassword,
          }),
        });
        const loginData = (await loginResponse.json()) as Record<string, unknown>;
        accessToken = loginData.accessToken as string;
      }

      // Call Trade API protected route with valid token
      const tradeResponse = await fetch(`${TRADE_API_URL}/api/v1/accounts/${testAccountId}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      // Should succeed with 200 OK
      expect(tradeResponse.status).toBe(200);
    });
  });

  describe('Rejection Cases: Trade API refuses untrusted tokens', () => {
    /**
     * Test: No token is rejected
     */
    it('should reject trade api request without token', async () => {
      const response = await fetch(`${TRADE_API_URL}/api/v1/accounts/${testAccountId}`);
      expect(response.status).toBe(401);
      const data = (await response.json()) as Record<string, unknown>;
      expect(data.errorCode).toBe('AUTH-401');
    });

    /**
     * Test: Token signed with wrong key is rejected
     * This verifies that Trade API validates the signature before reading claims
     */
    it('should reject trade api request with token signed by wrong key', async () => {
      // Create a well-formed token with correct claims but signed with a different key
      const wrongSecret = 'wrong-secret-key-that-trade-api-should-not-accept-';
      
      const tokenPayload = {
        sub: 'test-user-id',
        accountId: testAccountId,
        roles: ['CUSTOMER'],
        iss: JWT_ISSUER,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 900, // 15 minutes
      };
      
      // Sign with wrong key
      const wrongToken = jwt.sign(tokenPayload, wrongSecret, { algorithm: 'HS256' });

      const response = await fetch(`${TRADE_API_URL}/api/v1/accounts/${testAccountId}`, {
        headers: {
          Authorization: `Bearer ${wrongToken}`,
        },
      });
      expect(response.status).toBe(401);
      const data = (await response.json()) as Record<string, unknown>;
      expect(data.errorCode).toBe('AUTH-401');
    });

    /**
     * Test: Expired token is rejected
     * Verifies that Trade API checks the exp claim
     */
    it('should reject trade api request with expired token', async () => {
      // Create an expired token signed with correct key
      const expiredPayload = {
        sub: 'test-user-id',
        accountId: testAccountId,
        roles: ['CUSTOMER'],
        iss: JWT_ISSUER,
        iat: Math.floor(Date.now() / 1000) - 3600, // 1 hour ago
        exp: Math.floor(Date.now() / 1000) - 1800, // 30 minutes ago (expired)
      };
      
      const expiredToken = jwt.sign(expiredPayload, JWT_SECRET, { algorithm: 'HS256' });

      const response = await fetch(`${TRADE_API_URL}/api/v1/accounts/${testAccountId}`, {
        headers: {
          Authorization: `Bearer ${expiredToken}`,
        },
      });
      expect(response.status).toBe(401);
      const data = (await response.json()) as Record<string, unknown>;
      expect(data.errorCode).toBe('AUTH-401');
    });

    /**
     * Test: Malformed token is rejected
     */
    it('should reject trade api request with malformed token', async () => {
      const response = await fetch(`${TRADE_API_URL}/api/v1/accounts/${testAccountId}`, {
        headers: {
          Authorization: 'Bearer not.a.valid.jwt.token',
        },
      });
      expect(response.status).toBe(401);
      const data = (await response.json()) as Record<string, unknown>;
      expect(data.errorCode).toBe('AUTH-401');
    });
  });

  describe('Token Claims Verification', () => {
    /**
     * Test: Token contains exactly the required claims
     * Per contract: sub, accountId, roles, iat, exp, iss
     */
    it('should contain exactly required claims in access token', async () => {
      if (!accessToken) {
        const loginResponse = await fetch(`${AUTH_SERVICE_URL}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: testUsername,
            password: testPassword,
          }),
        });
        const loginData = (await loginResponse.json()) as Record<string, unknown>;
        accessToken = loginData.accessToken as string;
      }

      const decoded: any = jwt.decode(accessToken);
      
      // Verify required claims
      expect(decoded).toHaveProperty('sub'); // User ID
      expect(decoded).toHaveProperty('accountId'); // Account ID
      expect(decoded).toHaveProperty('roles'); // Array of roles
      expect(decoded).toHaveProperty('iat'); // Issued at
      expect(decoded).toHaveProperty('exp'); // Expiry
      expect(decoded).toHaveProperty('iss'); // Issuer
      
      // Verify values
      expect(typeof decoded.sub).toBe('string');
      expect(typeof decoded.accountId).toBe('number');
      expect(Array.isArray(decoded.roles)).toBe(true);
      expect(typeof decoded.iat).toBe('number');
      expect(typeof decoded.exp).toBe('number');
      expect(decoded.iss).toBe(JWT_ISSUER);
      
      // Expiry should be 15 minutes after issued
      const tokenLifetime = decoded.exp - decoded.iat;
      const expectedLifetime = 900; // 15 minutes in seconds
      expect(Math.abs(tokenLifetime - expectedLifetime)).toBeLessThan(5); // Allow 5 second skew
    });
  });

  describe('Configuration Validation', () => {
    /**
     * Test: Verify environment configuration is correct
     */
    it('should have correct JWT configuration from environment', () => {
      expect(JWT_SECRET).toBe('test-shared-secret-32-bytes-minimum-key');
      expect(JWT_ISSUER).toBe('auth-service');
      expect(JWT_SECRET.length).toBeGreaterThanOrEqual(32);
    });

    /**
     * Test: Both services are reachable
     */
    it('should have both services running', async () => {
      try {
        // Auth service should respond
        const authResponse = await fetch(`${AUTH_SERVICE_URL}/docs`);
        expect([200, 301, 302]).toContain(authResponse.status);
      } catch (error) {
        const errorMsg = error instanceof Error ? error.message : String(error);
        throw new Error(`Auth Service not reachable at ${AUTH_SERVICE_URL}: ${errorMsg}`);
      }

      // Note: Trade API health check skipped - no health endpoint available yet
      // When a health endpoint is added to Trade REST API, this test can be extended
    });
  });
});

/**
 * Skip these integration tests if services are not running
 * 
 * To run integration tests:
 * 1. Start Auth Service: cd sprint-08-auth-service && npm start
 * 2. Start Trade API: cd sprint-07-event-backbone && mvn spring-boot:run
 * 3. Run tests: npm test -- --testPathPattern=integration
 */
// Removed skipped placeholder suites — integration gating handled by RUN_INTEGRATION_TESTS

}
