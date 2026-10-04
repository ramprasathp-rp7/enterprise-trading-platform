import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import {
  HttpClient,
  HTTP_INTERCEPTORS,
} from '@angular/common/http';
import { AuthInterceptor } from './auth.interceptor';
import { CoreAuthService } from './core-auth.service';
import { ApiOriginsConfig } from '../config/api-origins';
import { JWTClaims } from './auth.models';

describe('AuthInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let authService: CoreAuthService;
  let apiOriginsConfig: ApiOriginsConfig;

  const mockJWTClaims: JWTClaims = {
    sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    accountId: 1,
    roles: ['CUSTOMER'],
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
    iss: 'auth-service',
  };

  const createMockJWT = (claims: JWTClaims): string => {
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify(claims));
    return `${header}.${payload}.signature`;
  };

  const mockToken = createMockJWT(mockJWTClaims);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        CoreAuthService,
        ApiOriginsConfig,
        AuthInterceptor,
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(CoreAuthService);
    apiOriginsConfig = TestBed.inject(ApiOriginsConfig);

    sessionStorage.clear();
  });

  afterEach(() => {
    httpTestingController.verify();
    sessionStorage.clear();
  });

  describe('Attaches Bearer token to protected requests', () => {
    it('should attach bearer token to GET /accounts', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      httpClient.get('/api/v1/accounts/1').subscribe(() => {});

      const req = httpTestingController.expectOne('/api/v1/accounts/1');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockToken}`);
      req.flush({});
    });

    it('should attach bearer token to POST /orders', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      httpClient.post('/api/v1/orders', { side: 'BUY' }).subscribe(() => {});

      const req = httpTestingController.expectOne('/api/v1/orders');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockToken}`);
      req.flush({});
    });

    it('should not attach token when no token is stored', () => {
      httpClient.get('/api/v1/accounts/1').subscribe(() => {});

      const req = httpTestingController.expectOne('/api/v1/accounts/1');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('Does NOT attach token to auth endpoints', () => {
    it('should NOT attach bearer token to POST /auth/login', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      // Set token in auth service
      authService.setTokens(mockToken, 'refresh-token', mockUser);

      // Make request to login endpoint
      httpClient
        .post('/auth/login', { username: 'user', password: 'pass' })
        .subscribe(() => {});

      const req = httpTestingController.expectOne('/auth/login');
      // Should NOT have Authorization header
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should NOT attach bearer token to POST /auth/register', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      // Set token in auth service
      authService.setTokens(mockToken, 'refresh-token', mockUser);

      // Make request to register endpoint
      httpClient
        .post('/auth/register', {
          username: 'newuser',
          password: 'password123',
          accountId: 1,
        })
        .subscribe(() => {});

      const req = httpTestingController.expectOne('/auth/register');
      // Should NOT have Authorization header
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should NOT attach bearer token to POST /auth/refresh', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      // Set token in auth service
      authService.setTokens(mockToken, 'refresh-token', mockUser);

      // Make request to refresh endpoint
      httpClient
        .post('/auth/refresh', { refreshToken: 'refresh-token' })
        .subscribe(() => {});

      const req = httpTestingController.expectOne('/auth/refresh');
      // Should NOT have Authorization header
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('Case-insensitive endpoint matching', () => {
    it('should NOT attach token to /AUTH/LOGIN regardless of case', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      httpClient.post('/AUTH/LOGIN', { username: 'user', password: 'pass' }).subscribe(() => {});

      const req = httpTestingController.expectOne('/AUTH/LOGIN');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should attach token to /AUTH/ME regardless of case', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      httpClient.get('/AUTH/ME').subscribe(() => {});

      const req = httpTestingController.expectOne('/AUTH/ME');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockToken}`);
      req.flush({});
    });
  });

  describe('Token format', () => {
    it('should use Bearer scheme for token', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      httpClient.get('/api/v1/accounts/1').subscribe(() => {});

      const req = httpTestingController.expectOne('/api/v1/accounts/1');
      expect(req.request.headers.get('Authorization')).toMatch(/^Bearer /);
      req.flush({});
    });
  });

  describe('Origin-based bearer token filtering', () => {
    /**
     * Test Case 1: Attaches bearer token to platform API request
     * 
     * Verifies that when a request is made to a URL with an allowed origin
     * (configured in apiOriginsConfig), the interceptor attaches the bearer token
     * to the Authorization header.
     * 
     * This test uses relative URLs (e.g., /api/v1/orders) which are same-origin
     * requests and should always be treated as allowed platform API calls.
     */
    it('attaches bearer token to platform API request', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      // Request to platform API (relative URL - same-origin, always allowed)
      httpClient.post('/api/v1/orders', { side: 'BUY', symbol: 'AAPL' }).subscribe(() => {});

      const req = httpTestingController.expectOne('/api/v1/orders');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockToken}`);
      req.flush({ orderId: '123', status: 'NEW' });
    });

    /**
     * Test Case 2: Does not attach bearer token to third-party origin request
     * 
     * Verifies that when a request is made to a URL with an origin NOT in the
     * allow-list (e.g., external analytics, CDN), the interceptor does NOT attach
     * the bearer token, even if one is available.
     * 
     * This is the critical security test: we must NOT send tokens to third-party services,
     * where they could be logged in access logs, error trackers, or analytics pipelines.
     */
    it('does not attach bearer token to third-party origin request', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      // Request to third-party origin (external analytics service)
      // This should NOT receive the bearer token
      const externalUrl = 'http://external-analytics.com/track';
      httpClient.post(externalUrl, { event: 'page-view' }).subscribe(() => {});

      const req = httpTestingController.expectOne(externalUrl);
      // Verify NO Authorization header is attached
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    /**
     * Test Case 3: Does not attach bearer token to unauthenticated auth routes
     * 
     * Verifies that public auth endpoints (/auth/login, /auth/register, /auth/refresh)
     * do not receive bearer tokens, even if one is available.
     * 
     * These endpoints either:
     * - Don't accept Authorization headers (login, register)
     * - Use a different authentication mechanism (refresh uses refresh token in body)
     */
    it('does not attach bearer token to unauthenticated auth routes', () => {
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      authService.setTokens(mockToken, 'refresh-token', mockUser);

      // POST to login endpoint (public, no Authorization header needed)
      httpClient
        .post('/auth/login', { username: 'user@example.com', password: 'password' })
        .subscribe(() => {});

      const req = httpTestingController.expectOne('/auth/login');
      // Verify NO Authorization header is attached to unauthenticated route
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({ accessToken: 'new-token', refreshToken: 'new-refresh' });
    });
  });
});
