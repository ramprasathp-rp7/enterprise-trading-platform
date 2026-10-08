import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthInterceptor } from './auth.interceptor';
import { CoreAuthService } from './core-auth.service';
import { environment } from '../config/environment';

const jwt = (expInSeconds: number, tag = 'a') => {
  const enc = (o: object) => btoa(JSON.stringify(o));
  const claims = { sub: 'u1', accountId: 7, roles: ['CUSTOMER'], username: 'rkumar', tag, exp: Math.floor(Date.now() / 1000) + expInSeconds };
  return `${enc({ alg: 'HS256' })}.${enc(claims)}.sig`;
};

describe('AuthInterceptor - automatic JWT refresh', () => {
  const refreshUrl = `${environment.AUTH_SERVICE_URL}/auth/refresh`;
  const user = { id: 'u1', username: 'rkumar', accountId: 7, roles: ['CUSTOMER'] };
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: CoreAuthService;
  let navigate: ReturnType<typeof vi.fn>;

  const tokens = (accessToken: string, refreshToken: string) => ({ accessToken, refreshToken, tokenType: 'Bearer', expiresIn: 900 });
  const bearer = (url: string) => backend.expectOne(url).request.headers.get('Authorization');

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(CoreAuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as any;
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    backend.verify();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('refreshes an expired JWT before sending, then sends with the new one', () => {
    const fresh = jwt(900, 'fresh');
    auth.setTokens(jwt(-60), 'refresh-1', user);
    let body: unknown;

    http.get('/api/v1/accounts/7').subscribe((b) => (body = b));

    backend.expectNone('/api/v1/accounts/7');
    const refresh = backend.expectOne(refreshUrl);
    expect(refresh.request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh.flush(tokens(fresh, 'refresh-2'));

    const req = backend.expectOne('/api/v1/accounts/7');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${fresh}`);
    req.flush({ ok: true });

    expect(body).toEqual({ ok: true });
    expect(auth.refreshToken()).toBe('refresh-2');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not refresh a JWT that is still valid', () => {
    const valid = jwt(900);
    auth.setTokens(valid, 'refresh-1', user);

    http.get('/api/v1/orders').subscribe();

    expect(bearer('/api/v1/orders')).toBe(`Bearer ${valid}`);
    backend.expectNone(refreshUrl);
  });

  it('refreshes once and retries when the API answers 401', () => {
    const fresh = jwt(900, 'fresh');
    auth.setTokens(jwt(900, 'server-revoked'), 'refresh-1', user);
    let body: unknown;

    http.get('/api/v1/orders').subscribe((b) => (body = b));
    backend.expectOne('/api/v1/orders').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush(tokens(fresh, 'refresh-2'));

    const retry = backend.expectOne('/api/v1/orders');
    expect(retry.request.headers.get('Authorization')).toBe(`Bearer ${fresh}`);
    retry.flush(['order']);

    expect(body).toEqual(['order']);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('uses a single refresh for many requests that expire together', () => {
    const fresh = jwt(900, 'fresh');
    auth.setTokens(jwt(-60), 'refresh-1', user);

    http.get('/api/v1/a').subscribe();
    http.get('/api/v1/b').subscribe();
    http.get('/api/v1/c').subscribe();

    backend.expectOne(refreshUrl).flush(tokens(fresh, 'refresh-2'));

    for (const url of ['/api/v1/a', '/api/v1/b', '/api/v1/c']) {
      expect(bearer(url)).toBe(`Bearer ${fresh}`);
    }
  });

  it('retries with the already-rotated token when another request refreshed meanwhile', () => {
    const old = jwt(900, 'old');
    const rotated = jwt(900, 'rotated');
    auth.setTokens(old, 'refresh-1', user);

    http.get('/api/v1/orders').subscribe();
    const first = backend.expectOne('/api/v1/orders');
    auth.setTokens(rotated, 'refresh-2', user);
    first.flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(bearer('/api/v1/orders')).toBe(`Bearer ${rotated}`);
    backend.expectNone(refreshUrl);
  });

  it('signs out and returns to sign-in when the refresh token is rejected', () => {
    auth.setTokens(jwt(-60), 'refresh-1', user);
    let status: number | undefined;

    http.get('/api/v1/orders').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne(refreshUrl).flush({ errorCode: 'AUTH-401' }, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('/api/v1/orders');
    expect(status).toBe(401);
    expect(auth.accessToken()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/sign-in'], expect.objectContaining({ replaceUrl: true }));
  });

  it('signs out when the retried request is still unauthorised', () => {
    auth.setTokens(jwt(900), 'refresh-1', user);
    let status: number | undefined;

    http.get('/api/v1/orders').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/api/v1/orders').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush(tokens(jwt(900, 'fresh'), 'refresh-2'));
    backend.expectOne('/api/v1/orders').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.accessToken()).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('signs out on 401 when there is no refresh token to use', () => {
    auth.setTokens(jwt(900), '', user);

    http.get('/api/v1/orders').subscribe({ error: () => {} });
    backend.expectOne('/api/v1/orders').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone(refreshUrl);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('passes other errors straight through without refreshing', () => {
    auth.setTokens(jwt(900), 'refresh-1', user);
    let status: number | undefined;

    http.get('/api/v1/orders').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/api/v1/orders').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
    backend.expectNone(refreshUrl);
    expect(navigate).not.toHaveBeenCalled();
  });
});
