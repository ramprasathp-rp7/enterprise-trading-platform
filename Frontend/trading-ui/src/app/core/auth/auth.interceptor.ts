import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse,
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { CoreAuthService } from './core-auth.service';
import { TokenRefreshService } from './token-refresh.service';
import { ApiOriginsConfig } from '../config/api-origins';
import { Router } from '@angular/router';

/**
 * Authentication Interceptor
 * 
 * Attaches JWT bearer token to requests destined for platform APIs only, and
 * keeps the session alive by refreshing the JWT through POST /auth/refresh.
 * Uses an allow-list of origins to prevent accidentally sending tokens to third-party services.
 * 
 * Token Attachment Decision Tree:
 * 1. Is the request origin in the allowed list? (via ApiOriginsConfig)
 *    - No: Skip token attachment (fail closed)
 *    - Yes: Continue to step 2
 * 2. Is this an unauthenticated auth endpoint (login, register, refresh)?
 *    - Yes: Skip token attachment
 *    - No: Attach token
 * 3. Has the access token expired (or is about to)?
 *    - Yes, and a refresh token is stored: refresh first, then send with the new token
 * 4. Did the API still answer 401?
 *    - Refresh once and retry; if the refresh fails, sign the user out
 * 
 * Never logs tokens or credentials.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private authService: CoreAuthService,
    private apiOriginsConfig: ApiOriginsConfig,
    private router: Router,
    private tokenRefresh: TokenRefreshService
  ) {}

  intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Step 1: Check if the request origin is in the allowed list
    if (!this.apiOriginsConfig.isAllowedOrigin(req.url)) {
      // Origin not allowed: skip token attachment
      return next.handle(req);
    }

    // Step 2: Check if this is an unauthenticated auth endpoint
    if (this.isUnauthenticatedAuthRoute(req)) {
      // This is a public auth endpoint: skip token attachment
      return next.handle(req);
    }

    const token = this.authService.accessToken();

    // Step 3: Expired (or nearly expired) access token - refresh before sending
    if (token && this.tokenRefresh.needsRefresh(token) && this.tokenRefresh.canRefresh()) {
      return this.tokenRefresh.refresh().pipe(
        catchError((error) => this.endSession(error)),
        switchMap((freshToken) => this.send(req, next, freshToken))
      );
    }

    if (!token) {
      // No token available: proceed without header
      return next.handle(req);
    }

    return this.send(req, next, token);
  }

  /** Send with the bearer token; on 401, refresh once and retry. */
  private send(req: HttpRequest<any>, next: HttpHandler, token: string): Observable<HttpEvent<any>> {
    return next.handle(this.withToken(req, token)).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status !== 401) {
          return throwError(() => error);
        }

        // Another request already rotated the token while this one was in flight
        const current = this.authService.accessToken();
        if (current && current !== token && !this.tokenRefresh.needsRefresh(current)) {
          return next.handle(this.withToken(req, current)).pipe(catchError((retryError) => this.handleRetryError(retryError)));
        }

        if (!this.tokenRefresh.canRefresh()) {
          return this.endSession(error);
        }

        return this.tokenRefresh.refresh().pipe(
          catchError(() => this.endSession(error)),
          switchMap((freshToken) =>
            next.handle(this.withToken(req, freshToken)).pipe(catchError((retryError) => this.handleRetryError(retryError)))
          )
        );
      })
    );
  }

  /** A retried request that is still unauthorised means the session is gone. */
  private handleRetryError(error: HttpErrorResponse): Observable<never> {
    return error.status === 401 ? this.endSession(error) : throwError(() => error);
  }

  private withToken(req: HttpRequest<any>, token: string): HttpRequest<any> {
    return req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  /** Clear tokens and send the user to sign-in, coming back here afterwards. */
  private endSession(error: unknown): Observable<never> {
    console.warn('Session expired - signing out');
    this.authService.clearTokens();
    const returnUrl = this.router.url;
    const extras = returnUrl && !returnUrl.startsWith('/sign-in') ? { queryParams: { returnUrl } } : {};
    this.router.navigate(['/sign-in'], { replaceUrl: true, ...extras }).catch((navError) => {
      console.error('Failed to navigate to sign-in:', navError);
    });
    return throwError(() => error);
  }

  /**
   * Check if this is an unauthenticated auth endpoint.
   * 
   * These endpoints don't require (or accept) a bearer token in the Authorization header:
   * - POST /auth/login (public login endpoint)
   * - POST /auth/register (public registration endpoint)
   * - POST /auth/refresh (refresh endpoint uses refresh token in request body, not header)
   * 
   * @param req The HTTP request
   * @returns true if this is an unauthenticated auth route, false otherwise
   */
  private isUnauthenticatedAuthRoute(req: HttpRequest<any>): boolean {
    const url = req.url.toLowerCase();
    const method = req.method.toUpperCase();

    // Skip login endpoint
    if (url.includes('/auth/login') && method === 'POST') {
      return true;
    }

    // Skip register endpoint
    if (url.includes('/auth/register') && method === 'POST') {
      return true;
    }

    // Skip refresh endpoint (uses refresh token in request body)
    if (url.includes('/auth/refresh') && method === 'POST') {
      return true;
    }

    return false;
  }
}
