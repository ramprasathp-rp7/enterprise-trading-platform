import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { CoreAuthService } from './core-auth.service';

/**
 * Authentication Interceptor
 * 
 * Adds JWT bearer token to all HTTP requests except login/register endpoints.
 * Never logs tokens or credentials.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private authService: CoreAuthService) {}

  intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Don't attach token to auth endpoints that don't require it
    if (this.shouldSkipTokenAttachment(req)) {
      return next.handle(req);
    }

    // Get current token
    const token = this.authService.accessToken();
    if (!token) {
      return next.handle(req);
    }

    // Clone request and add Authorization header
    const authenticatedRequest = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });

    return next.handle(authenticatedRequest);
  }

  /**
   * Determine if this request should skip token attachment
   * 
   * We skip auth endpoints that don't require authentication:
   * - POST /auth/login (public login)
   * - POST /auth/register (public registration)
   * - POST /auth/refresh (refresh token endpoint, uses refresh token in body)
   */
  private shouldSkipTokenAttachment(req: HttpRequest<any>): boolean {
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
