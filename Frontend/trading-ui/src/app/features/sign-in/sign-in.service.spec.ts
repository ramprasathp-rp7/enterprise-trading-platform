import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { SignInService } from './sign-in.service';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { AuthService, TokenResponse, UserResponse, ProfileService } from '../../api/generated/auth';
import { of, throwError } from 'rxjs';

describe('SignInService', () => {
  let service: SignInService;
  let coreAuthService: CoreAuthService;
  let authApiService: any;
  let profileApiService: any;

  const mockJWTToken =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.' +
    btoa(
      JSON.stringify({
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: 1,
        roles: ['CUSTOMER'],
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 900,
        iss: 'auth-service',
      })
    ) +
    '.signature';

  const mockTokenResponse: TokenResponse = {
    accessToken: mockJWTToken,
    refreshToken: 'mock-refresh-token-123',
    tokenType: TokenResponse.TokenTypeEnum.Bearer,
    expiresIn: 900,
  };

  const mockUserResponse: UserResponse = {
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    accountId: 1,
    roles: ['CUSTOMER'],
    createdOn: '2026-10-05T08:00:00Z',
  };

  beforeEach(() => {
    authApiService = {
      login: vi.fn(),
    };

    profileApiService = {
      getCurrentUser: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        SignInService,
        CoreAuthService,
        { provide: AuthService, useValue: authApiService },
        { provide: ProfileService, useValue: profileApiService },
      ],
    });

    service = TestBed.inject(SignInService);
    coreAuthService = TestBed.inject(CoreAuthService);

    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  describe('login', () => {
    it('should store tokens on successful login', async () => {
      authApiService.login.mockReturnValue(of(mockTokenResponse));
      profileApiService.getCurrentUser.mockReturnValue(of(mockUserResponse));

      const user = await service.login({ username: 'priya.menon', password: 'correct horse battery staple' }).toPromise();

      expect(authApiService.login).toHaveBeenCalledWith({
        username: 'priya.menon',
        password: 'correct horse battery staple',
      });
      expect(coreAuthService.accessToken()).toBe(mockJWTToken);
      expect(coreAuthService.refreshToken()).toBe('mock-refresh-token-123');
      expect(coreAuthService.isAuthenticated()).toBe(true);
      expect(user).toEqual(mockUserResponse);
    });

    it('should return user response on successful login', async () => {
      authApiService.login.mockReturnValue(of(mockTokenResponse));
      profileApiService.getCurrentUser.mockReturnValue(of(mockUserResponse));

      const user = await service.login({ username: 'priya.menon', password: 'password' }).toPromise();

      expect(user?.username).toBe('priya.menon');
      expect(user?.accountId).toBe(1);
      expect(user?.id).toBe('8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f');
    });

    it('should clear tokens and error on login failure', async () => {
      const authError = { errorCode: 'AUTH-401', message: 'Unauthorised' };
      authApiService.login.mockReturnValue(throwError(() => authError));

      try {
        await service.login({ username: 'priya.menon', password: 'wrong-password' }).toPromise();
      } catch (error) {
        expect(coreAuthService.accessToken()).toBeNull();
        expect(coreAuthService.refreshToken()).toBeNull();
        expect(coreAuthService.isAuthenticated()).toBe(false);
        expect(error).toEqual(authError);
      }
    });

    it('should handle login with invalid credentials (AUTH-401)', async () => {
      const authError = { errorCode: 'AUTH-401', message: 'Unauthorised' };
      authApiService.login.mockReturnValue(throwError(() => authError));

      try {
        await service.login({ username: 'priya.menon', password: 'wrong' }).toPromise();
      } catch (error) {
        expect((error as any).errorCode).toBe('AUTH-401');
      }
    });

    it('should handle validation errors (VAL-422)', async () => {
      const validationError = { errorCode: 'VAL-422', message: 'Invalid input' };
      authApiService.login.mockReturnValue(throwError(() => validationError));

      try {
        await service.login({ username: '', password: '' }).toPromise();
      } catch (error) {
        expect((error as any).errorCode).toBe('VAL-422');
      }
    });
  });

  describe('getCurrentUser', () => {
    it('should fetch and return current user', async () => {
      coreAuthService.setTokens(mockJWTToken, 'refresh-token', null);
      profileApiService.getCurrentUser.mockReturnValue(of(mockUserResponse));

      const user = await service.getCurrentUser().toPromise();

      expect(profileApiService.getCurrentUser).toHaveBeenCalled();
      expect(user).toEqual(mockUserResponse);
      expect(coreAuthService.currentUser()).toEqual({
        id: mockUserResponse.id,
        username: mockUserResponse.username,
        accountId: mockUserResponse.accountId,
        roles: mockUserResponse.roles,
      });
    });

    it('should update CoreAuthService user on success', async () => {
      coreAuthService.setTokens(mockJWTToken, 'refresh-token', null);
      profileApiService.getCurrentUser.mockReturnValue(of(mockUserResponse));

      await service.getCurrentUser().toPromise();

      const currentUser = coreAuthService.currentUser();
      expect(currentUser?.username).toBe('priya.menon');
      expect(currentUser?.accountId).toBe(1);
    });

    it('should propagate error if getCurrentUser fails', async () => {
      const error = { errorCode: 'AUTH-401', message: 'Unauthorised' };
      profileApiService.getCurrentUser.mockReturnValue(throwError(() => error));

      try {
        await service.getCurrentUser().toPromise();
      } catch (err) {
        expect(err).toEqual(error);
      }
    });
  });

  describe('logout', () => {
    it('should clear all tokens and session', () => {
      coreAuthService.setTokens(mockJWTToken, 'refresh-token', mockUserResponse);
      expect(coreAuthService.isAuthenticated()).toBe(true);
      expect(sessionStorage.getItem('access_token')).toBe(mockJWTToken);

      service.logout();

      expect(coreAuthService.accessToken()).toBeNull();
      expect(coreAuthService.refreshToken()).toBeNull();
      expect(coreAuthService.currentUser()).toBeNull();
      expect(coreAuthService.isAuthenticated()).toBe(false);
      expect(sessionStorage.getItem('access_token')).toBeNull();
    });
  });

  describe('refreshToken', () => {
    it('should refresh access token using refresh token', async () => {
      coreAuthService.setTokens(mockJWTToken, 'refresh-token-123', mockUserResponse);

      const newTokenResponse: TokenResponse = {
        accessToken: 'new-access-token',
        refreshToken: 'new-refresh-token',
        tokenType: TokenResponse.TokenTypeEnum.Bearer,
        expiresIn: 900,
      };

      authApiService.refresh = vi.fn().mockReturnValue(of(newTokenResponse));

      const response = await service.refreshToken().toPromise();

      expect(authApiService.refresh).toHaveBeenCalledWith({
        refreshToken: 'refresh-token-123',
      });
      expect(response?.accessToken).toBe('new-access-token');
      expect(coreAuthService.accessToken()).toBe('new-access-token');
      expect(coreAuthService.refreshToken()).toBe('new-refresh-token');
    });

    it('should clear tokens on refresh failure', async () => {
      coreAuthService.setTokens(mockJWTToken, 'refresh-token-123', mockUserResponse);

      const error = { errorCode: 'AUTH-401', message: 'Unauthorised' };
      authApiService.refresh = vi.fn().mockReturnValue(throwError(() => error));

      try {
        await service.refreshToken().toPromise();
      } catch (err) {
        expect(coreAuthService.accessToken()).toBeNull();
        expect(coreAuthService.isAuthenticated()).toBe(false);
      }
    });

    it('should error when no refresh token available', async () => {
      expect(coreAuthService.refreshToken()).toBeNull();

      try {
        await service.refreshToken().toPromise();
      } catch (err) {
        expect((err as any).message).toContain('No refresh token available');
      }
    });
  });
});
