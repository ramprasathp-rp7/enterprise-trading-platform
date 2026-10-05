import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { adminGuard } from './admin.guard';
import { unauthenticatedGuard } from './unauthenticated.guard';
import { CoreAuthService } from './core-auth.service';

describe('route guards', () => {
  let auth: {
    isAdmin: ReturnType<typeof vi.fn>;
    isAuthenticated: ReturnType<typeof vi.fn>;
    getDashboardRoute: ReturnType<typeof vi.fn>;
  };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/admin/analytics' } as RouterStateSnapshot;

  beforeEach(() => {
    auth = {
      isAdmin: vi.fn(() => false),
      isAuthenticated: vi.fn(() => false),
      getDashboardRoute: vi.fn(() => '/dashboard'),
    };
    router = { navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: CoreAuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  describe('adminGuard', () => {
    it('lets admins through', () => {
      auth.isAdmin.mockReturnValue(true);

      const result = TestBed.runInInjectionContext(() => adminGuard(route, state));

      expect(result).toBe(true);
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('sends non-admins to the customer dashboard', () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});

      const result = TestBed.runInInjectionContext(() => adminGuard(route, state));

      expect(result).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
      warn.mockRestore();
    });
  });

  describe('unauthenticatedGuard', () => {
    it('allows the sign-in page when signed out', () => {
      const result = TestBed.runInInjectionContext(() => unauthenticatedGuard(route, state));

      expect(result).toBe(true);
    });

    it('redirects a signed-in user to their role dashboard', () => {
      auth.isAuthenticated.mockReturnValue(true);
      auth.getDashboardRoute.mockReturnValue('/admin/analytics');

      const result = TestBed.runInInjectionContext(() => unauthenticatedGuard(route, state));

      expect(result).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/analytics');
    });
  });
});
