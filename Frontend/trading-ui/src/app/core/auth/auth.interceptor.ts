import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse,
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CoreAuthService } from './core-auth.service';
import { ApiOriginsConfig } from '../config/api-origins';
import { Router } from '@angular/router';

/**
 * Authentication Interceptor
 * 
 * Attaches JWT bearer token to requests destined for platform APIs only.
 * Uses an allow-list of origins to prevent accidentally sending tokens to third-party services.
 * 
 * Token Attachment Decision Tree:
 * 1. Is the request origin in the allowed list? (via ApiOriginsConfig)
 *    - No: Skip token attachment (fail closed)
 *    - Yes: Continue to step 2
 * 2. Is this an unauthenticated auth endpoint (login, register, refresh)?
 *    - Yes: Skip token attachment
 *    - No: Attach token
 * 
 * Never logs tokens or credentials.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private authService: CoreAuthService,
    private apiOriginsConfig: ApiOriginsConfig,
    private router: Router
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

    // Step 3: Attach token to this platform API request
    const token = this.authService.accessToken();
    if (!token) {
      // No token available: proceed without header
      return next.handle(req);
    }

    // Clone request and add Authorization header
    const authenticatedRequest = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });

    return next.handle(authenticatedRequest).pipe(
      catchError((error: HttpErrorResponse) => {
        // Handle 401 Unauthorized - clear tokens and redirect to login
        if (error.status === 401) {
          console.warn('Received 401 Unauthorized response - logging out user');
          this.authService.clearTokens();
          this.router.navigate(['/sign-in'], { replaceUrl: true }).catch((navError) => {
            console.error('Failed to navigate to sign-in:', navError);
          });
        }

        return throwError(() => error);
      })
    );
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
