import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpInterceptorFn, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TokenRefreshService } from './token-refresh.service';
import { CoreAuthService } from './core-auth.service';
import { environment } from '../config/environment';

const jwt = (expInSeconds: number) => {
  const enc = (o: object) => btoa(JSON.stringify(o));
  const claims = { sub: 'u1', accountId: 7, roles: ['CUSTOMER'], username: 'rkumar', exp: Math.floor(Date.now() / 1000) + expInSeconds };
  return `${enc({ alg: 'HS256' })}.${enc(claims)}.sig`;
};

describe('TokenRefreshService', () => {
  const refreshUrl = `${environment.AUTH_SERVICE_URL}/auth/refresh`;
  let service: TokenRefreshService;
  let auth: CoreAuthService;
  let http: HttpTestingController;
  let interceptedUrls: string[];

  beforeEach(() => {
    sessionStorage.clear();
    interceptedUrls = [];
    const spy: HttpInterceptorFn = (req, next) => {
      interceptedUrls.push(req.url);
      return next(req);
    };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([spy])), provideHttpClientTesting()],
    });
    service = TestBed.inject(TokenRefreshService);
    auth = TestBed.inject(CoreAuthService);
    http = TestBed.inject(HttpTestingController);
    auth.setTokens(jwt(-10), 'refresh-1', { id: 'u1', username: 'rkumar', accountId: 7, roles: ['CUSTOMER'] });
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('knows when a token needs refreshing', () => {
    expect(service.needsRefresh(null)).toBe(true);
    expect(service.needsRefresh('garbage')).toBe(true);
    expect(service.needsRefresh(jwt(-5))).toBe(true);
    expect(service.needsRefresh(jwt(20))).toBe(true);
    expect(service.needsRefresh(jwt(600))).toBe(false);
    expect(service.canRefresh()).toBe(true);
  });

  it('exchanges the refresh token for a new pair and stores it', () => {
    const fresh = jwt(900);
    let emitted: string | undefined;

    service.refresh().subscribe((t) => (emitted = t));
    const req = http.expectOne(refreshUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ accessToken: fresh, refreshToken: 'refresh-2', tokenType: 'Bearer', expiresIn: 900 });

    expect(emitted).toBe(fresh);
    expect(auth.accessToken()).toBe(fresh);
    expect(auth.refreshToken()).toBe('refresh-2');
    expect(sessionStorage.getItem('refresh_token')).toBe('refresh-2');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.currentUser()?.username).toBe('rkumar');
  });

  it('sends the refresh call straight to the backend, around the interceptors', () => {
    service.refresh().subscribe();
    http.expectOne(refreshUrl).flush({ accessToken: 'A', refreshToken: 'refresh-2', tokenType: 'Bearer', expiresIn: 900 });

    expect(interceptedUrls).toEqual([]);
  });

  it('shares one request between concurrent callers, so the rotated token is never replayed', () => {
    const results: string[] = [];
    service.refresh().subscribe((t) => results.push(t));
    service.refresh().subscribe((t) => results.push(t));
    service.refresh().subscribe((t) => results.push(t));

    http.expectOne(refreshUrl).flush({ accessToken: 'A', refreshToken: 'refresh-2', tokenType: 'Bearer', expiresIn: 900 });

    expect(results).toEqual(['A', 'A', 'A']);

    // Later refreshes use the rotated token
    service.refresh().subscribe();
    expect(http.expectOne(refreshUrl).request.body).toEqual({ refreshToken: 'refresh-2' });
  });

  it('clears the session when the refresh token is rejected', () => {
    let failed = false;
    service.refresh().subscribe({ error: () => (failed = true) });

    http.expectOne(refreshUrl).flush({ errorCode: 'AUTH-401' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.accessToken()).toBeNull();
    expect(auth.refreshToken()).toBeNull();
    expect(service.canRefresh()).toBe(false);
  });

  it('errors without calling the API when there is no refresh token', () => {
    auth.clearTokens();
    let failed = false;

    service.refresh().subscribe({ error: () => (failed = true) });

    expect(failed).toBe(true);
    http.expectNone(refreshUrl);
  });
});
