import { TestBed } from '@angular/core/testing';
import { CoreAuthService } from './core-auth.service';
import { JWTClaims } from './auth.models';

describe('CoreAuthService', () => {
  let service: CoreAuthService;

  // Mock JWT tokens
  const mockJWTClaims: JWTClaims = {
    sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    accountId: 1,
    roles: ['CUSTOMER'],
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900, // 15 minutes in future
    iss: 'auth-service',
  };

  const mockExpiredJWTClaims: JWTClaims = {
    ...mockJWTClaims,
    exp: Math.floor(Date.now() / 1000) - 100, // 100 seconds in past
  };

  const createMockJWT = (claims: JWTClaims): string => {
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify(claims));
    const signature = 'mock-signature';
    return `${header}.${payload}.${signature}`;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CoreAuthService],
    });
    service = TestBed.inject(CoreAuthService);
    // Clear sessionStorage before each test
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  describe('Token Storage', () => {
    it('should store tokens in sessionStorage and signals', () => {
      const mockToken = createMockJWT(mockJWTClaims);
      const mockRefreshToken = 'mock-refresh-token';
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      service.setTokens(mockToken, mockRefreshToken, mockUser);

      expect(service.accessToken()).toBe(mockToken);
      expect(service.refreshToken()).toBe(mockRefreshToken);
      expect(service.currentUser()).toEqual(mockUser);
      expect(sessionStorage.getItem('access_token')).toBe(mockToken);
      expect(sessionStorage.getItem('refresh_token')).toBe(mockRefreshToken);
    });

    it('should clear tokens from signals and sessionStorage', () => {
      const mockToken = createMockJWT(mockJWTClaims);
      const mockRefreshToken = 'mock-refresh-token';
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      service.setTokens(mockToken, mockRefreshToken, mockUser);
      service.clearTokens();

      expect(service.accessToken()).toBeNull();
      expect(service.refreshToken()).toBeNull();
      expect(service.currentUser()).toBeNull();
      expect(sessionStorage.getItem('access_token')).toBeNull();
      expect(sessionStorage.getItem('refresh_token')).toBeNull();
    });
  });

  describe('JWT Decoding', () => {
    it('should decode valid JWT and extract claims', () => {
      const mockToken = createMockJWT(mockJWTClaims);
      const decoded = service.decodeJWT(mockToken);

      expect(decoded.sub).toBe(mockJWTClaims.sub);
      expect(decoded.accountId).toBe(mockJWTClaims.accountId);
      expect(decoded.roles).toEqual(mockJWTClaims.roles);
      expect(decoded.exp).toBe(mockJWTClaims.exp);
      expect(decoded.iss).toBe(mockJWTClaims.iss);
    });

    it('should throw error on invalid JWT format', () => {
      expect(() => service.decodeJWT('invalid.token')).toThrowError();
      expect(() => service.decodeJWT('invalid')).toThrowError();
      expect(() => service.decodeJWT('')).toThrowError();
    });
  });

  describe('Token Expiration', () => {
    it('should detect expired token', () => {
      expect(service.isTokenExpired(mockExpiredJWTClaims)).toBe(true);
    });

    it('should detect valid (not expired) token', () => {
      expect(service.isTokenExpired(mockJWTClaims)).toBe(false);
    });

    it('should calculate seconds until expiry', () => {
      const mockToken = createMockJWT(mockJWTClaims);
      const secondsUntilExpiry = service.getSecondsUntilExpiry(mockToken);

      expect(secondsUntilExpiry).toBeTruthy();
      expect(secondsUntilExpiry).toBeGreaterThan(0);
      expect(secondsUntilExpiry).toBeLessThanOrEqual(900);
    });

    it('should return 0 for already expired token', () => {
      const mockToken = createMockJWT(mockExpiredJWTClaims);
      const secondsUntilExpiry = service.getSecondsUntilExpiry(mockToken);

      expect(secondsUntilExpiry).toBe(0);
    });
  });

  describe('isAuthenticated Signal', () => {
    it('should be false when no token is set', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should be true when valid token is set', () => {
      const mockToken = createMockJWT(mockJWTClaims);
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      service.setTokens(mockToken, 'refresh-token', mockUser);
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should be false when expired token is set', () => {
      const mockToken = createMockJWT(mockExpiredJWTClaims);
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      service.setTokens(mockToken, 'refresh-token', mockUser);
      expect(service.isAuthenticated()).toBe(false);
    });
  });

  describe('Error Management', () => {
    it('should set and clear error state', () => {
      expect(service.error()).toBeNull();
      expect(service.errorCode()).toBeNull();

      service.setError('Test error', 'AUTH-401');
      expect(service.error()).toBe('Test error');
      expect(service.errorCode()).toBe('AUTH-401');

      service.clearError();
      expect(service.error()).toBeNull();
      expect(service.errorCode()).toBeNull();
    });

    it('should clear errors when setting tokens', () => {
      service.setError('Previous error', 'AUTH-401');
      const mockToken = createMockJWT(mockJWTClaims);
      const mockUser = {
        id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        username: 'priya.menon',
        accountId: 1,
        roles: ['CUSTOMER'],
      };

      service.setTokens(mockToken, 'refresh-token', mockUser);
      expect(service.error()).toBeNull();
      expect(service.errorCode()).toBeNull();
    });
  });

  describe('shouldRefreshToken', () => {
    it('should return true when less than 1 minute remains', () => {
      const claimsNearExpiry: JWTClaims = {
        ...mockJWTClaims,
        exp: Math.floor(Date.now() / 1000) + 30, // 30 seconds in future
      };
      const mockToken = createMockJWT(claimsNearExpiry);

      expect(service.shouldRefreshToken(mockToken)).toBe(true);
    });

    it('should return false when more than 1 minute remains', () => {
      const mockToken = createMockJWT(mockJWTClaims);
      expect(service.shouldRefreshToken(mockToken)).toBe(false);
    });
  });
});
