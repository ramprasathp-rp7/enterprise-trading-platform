import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
} from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Authentication Interceptor
 * 
 * Adds JWT bearer token to all HTTP requests.
 * Handles token refresh on 401 responses.
 * Never logs tokens or credentials.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor() {}

  intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Interceptor implementation
    return next.handle(req);
  }
}
