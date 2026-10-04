import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';
import { authGuard } from './auth.guard';
import { CoreAuthService } from './core-auth.service';

describe('authGuard', () => {
  let authService: { isAuthenticated: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = {
      isAuthenticated: vi.fn(),
    };

    router = {
      createUrlTree: vi.fn(() => ({ redirectedTo: '/sign-in' } as unknown as UrlTree)),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: CoreAuthService, useValue: authService },
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
});