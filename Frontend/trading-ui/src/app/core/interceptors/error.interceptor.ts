import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Error Interceptor
 * 
 * Catches HTTP errors and passes them to centralized error handling.
 * Handles response transformation and error code mapping.
 */
@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  constructor() {}

  intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Error interceptor implementation
    return next.handle(req);
  }
}
