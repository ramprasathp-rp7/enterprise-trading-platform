import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { CoreAuthService } from './core-auth.service';

/**
 * Session restore, username resolution and role handling.
 * (Token storage/expiry basics are covered in core-auth.service.spec.ts.)
 */
describe('CoreAuthService – session and roles', () => {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  const token = (claims: object) => `${b64({ alg: 'none' })}.${b64({ sub: 'u-1', accountId: 7, exp: 4102444800, iat: 0, ...claims })}.sig`;

  const create = () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    return TestBed.inject(CoreAuthService);
  };

  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  describe('restoring a session from sessionStorage', () => {
    it('rebuilds the user from the stored access token', () => {
      sessionStorage.setItem('access_token', token({ username: 'rkumar', roles: ['ROLE_CUSTOMER'] }));

      const service = create();

      expect(service.isAuthenticated()).toBe(true);
      expect(service.currentUser()).toEqual({ id: 'u-1', username: 'rkumar', accountId: 7, roles: ['CUSTOMER'] });
    });

    it('prefers a non-generic name over the "trader" placeholder', () => {
      sessionStorage.setItem('access_token', token({ username: 'trader', preferred_username: 'priya', roles: ['CUSTOMER'] }));

      expect(create().currentUser()?.username).toBe('priya');
    });

    it('falls back to the email prefix, then the name claim', () => {
      sessionStorage.setItem('access_token', token({ email: 'asha@example.com', roles: [] }));
      expect(create().currentUser()?.username).toBe('asha');

      sessionStorage.setItem('access_token', token({ name: '  Ravi  ', roles: [] }));
      expect(create().currentUser()?.username).toBe('Ravi');
    });

    it('uses the stored username, then "User", when the token has no name', () => {
      sessionStorage.setItem('access_token', token({ username: 'trader', roles: [] }));
      sessionStorage.setItem('auth_username', 'stored-name');
      expect(create().currentUser()?.username).toBe('stored-name');

      sessionStorage.removeItem('auth_username');
      expect(create().currentUser()?.username).toBe('User');
    });

    it('merges roles from roles, role, authorities and scope claims without duplicates', () => {
      sessionStorage.setItem(
        'access_token',
        token({ roles: ['ROLE_CUSTOMER'], role: 'admin', authorities: ['ROLE_ADMIN', 42], scope: 'read  write,customer' })
      );

      expect(create().currentUser()?.roles).toEqual(['CUSTOMER', 'ADMIN', 'READ', 'WRITE']);
    });

    it('logs and stays signed out when the stored token cannot be decoded', () => {
      sessionStorage.setItem('access_token', 'garbage');
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      const service = create();

      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
      expect(consoleError).toHaveBeenCalled();
      consoleError.mockRestore();
    });
  });

  describe('setTokens', () => {
    it('normalises roles and persists the resolved username', () => {
      const service = create();

      service.setTokens(token({}), 'refresh', { id: 'u-1', username: ' jdoe ', accountId: 7, roles: ['role_admin', '', 5 as any] });

      expect(service.currentUser()?.roles).toEqual(['ADMIN']);
      expect(sessionStorage.getItem('auth_username')).toBe('jdoe');
    });

    it('resolves the username from the access token when the user has none', () => {
      const service = create();

      service.setTokens(token({ username: 'from-token' }), 'refresh', { id: 'u-1', username: '', accountId: 7, roles: [] });

      expect(service.currentUser()?.username).toBe('from-token');
    });

    it('falls back to "User" when nothing provides a username', () => {
      const service = create();

      service.setTokens('not-a-jwt', 'refresh', { id: 'u-1', username: '', accountId: 7 } as any);

      expect(service.currentUser()?.username).toBe('User');
      expect(service.currentUser()?.roles).toEqual([]);
    });

    it('accepts a null user', () => {
      const service = create();

      service.setTokens(token({}), 'refresh', null);

      expect(service.currentUser()).toBeNull();
      expect(sessionStorage.getItem('access_token')).not.toBeNull();
    });
  });

  describe('setUser', () => {
    it('normalises roles on the given user and accepts null', () => {
      const service = create();

      service.setUser({ id: 'u-1', username: 'a', accountId: 7, roles: [' ROLE_Admin '] });
      expect(service.currentUser()?.roles).toEqual(['ADMIN']);

      service.setUser({ id: 'u-1', username: 'a', accountId: 7 } as any);
      expect(service.currentUser()?.roles).toEqual([]);

      service.setUser(null);
      expect(service.currentUser()).toBeNull();
    });
  });

  describe('role helpers', () => {
    it('reports no role and the customer dashboard when signed out', () => {
      const service = create();

      expect(service.getUserRole()).toBeNull();
      expect(service.hasRole('ADMIN')).toBe(false);
      expect(service.isAdmin()).toBe(false);
      expect(service.getDashboardRoute()).toBe('/dashboard');
    });

    it('treats an empty role list as no role', () => {
      const service = create();
      service.setUser({ id: 'u-1', username: 'a', accountId: 7, roles: [] });

      expect(service.getUserRole()).toBeNull();
    });

    it('routes admins to the admin analytics dashboard', () => {
      const service = create();
      service.setUser({ id: 'u-1', username: 'a', accountId: 7, roles: ['ROLE_ADMIN', 'CUSTOMER'] });

      expect(service.getUserRole()).toBe('ADMIN');
      expect(service.hasRole('role_customer')).toBe(true);
      expect(service.isAdmin()).toBe(true);
      expect(service.getDashboardRoute()).toBe('/admin/analytics');
    });

    it('exposes role extraction from raw claims', () => {
      const service = create();

      expect(service.extractRolesFromClaims({ roles: 'customer' } as any)).toEqual(['CUSTOMER']);
      expect(service.extractRolesFromClaims({} as any)).toEqual([]);
    });
  });

  it('returns null expiry for an undecodable token and never asks to refresh it', () => {
    const service = create();

    expect(service.getSecondsUntilExpiry('bad')).toBeNull();
    expect(service.shouldRefreshToken('bad')).toBe(false);
  });
});
