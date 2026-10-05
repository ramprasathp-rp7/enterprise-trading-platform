import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { authGuard, canActivateChildGuard } from './auth.guard';
import { CoreAuthService } from './core-auth.service';
import { TokenRefreshService } from './token-refresh.service';

describe('authGuard', () => {
  let authService: { isAuthenticated: ReturnType<typeof vi.fn> };
  let tokenRefresh: { canRefresh: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = {
      isAuthenticated: vi.fn(),
    };
    tokenRefresh = {
      canRefresh: vi.fn(() => false),
      refresh: vi.fn(() => of('new-access-token')),
    };

    router = {
      createUrlTree: vi.fn(() => ({ redirectedTo: '/sign-in' } as unknown as UrlTree)),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: CoreAuthService, useValue: authService },
        { provide: TokenRefreshService, useValue: tokenRefresh },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows authenticated navigation', () => {
    authService.isAuthenticated.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/dashboard' } as any)
    );

    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
    expect(tokenRefresh.refresh).not.toHaveBeenCalled();
  });

  it('blocks unauthenticated navigation and redirects to sign-in with returnUrl', () => {
    authService.isAuthenticated.mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/blotter?status=OPEN' } as any)
    );

    expect(router.createUrlTree).toHaveBeenCalledWith(['/sign-in'], {
      queryParams: { returnUrl: '/blotter?status=OPEN' },
    });
    expect(result).toEqual({ redirectedTo: '/sign-in' });
  });

  it('gets a new JWT instead of signing out when the access token expired but a refresh token is stored', async () => {
    authService.isAuthenticated.mockReturnValue(false);
    tokenRefresh.canRefresh.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => authGuard({} as any, { url: '/positions' } as any));

    expect(await firstValueFrom(result as Observable<unknown>)).toBe(true);
    expect(tokenRefresh.refresh).toHaveBeenCalledTimes(1);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to sign-in when the refresh fails', async () => {
    authService.isAuthenticated.mockReturnValue(false);
    tokenRefresh.canRefresh.mockReturnValue(true);
    tokenRefresh.refresh.mockReturnValue(throwError(() => new Error('revoked')));

    const result = TestBed.runInInjectionContext(() => canActivateChildGuard({} as any, { url: '/positions' } as any));

    expect(await firstValueFrom(result as Observable<unknown>)).toEqual({ redirectedTo: '/sign-in' });
    expect(router.createUrlTree).toHaveBeenCalledWith(['/sign-in'], { queryParams: { returnUrl: '/positions' } });
  });

  it('always allows the sign-in page', () => {
    authService.isAuthenticated.mockReturnValue(false);

    expect(TestBed.runInInjectionContext(() => canActivateChildGuard({} as any, { url: '/sign-in' } as any))).toBe(true);
  });
});
