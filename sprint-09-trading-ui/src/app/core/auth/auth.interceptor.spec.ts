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
import { JWTClaims } from './auth.models';

describe('AuthInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let authService: CoreAuthService;

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
        AuthInterceptor,
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(CoreAuthService);

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
});
