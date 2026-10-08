import { inject } from '@angular/core';
import { CanActivateFn, CanActivateChildFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { CoreAuthService } from './core-auth.service';
import { TokenRefreshService } from './token-refresh.service';

/**
 * Shared check for both guards: allow when the JWT is valid; when it has expired
 * but a refresh token is stored, get a new JWT first; otherwise go to sign-in.
 */
function requireSession(url: string, label: string): boolean | UrlTree | Observable<boolean | UrlTree> {
  const authService = inject(CoreAuthService);
  const tokenRefresh = inject(TokenRefreshService);
  const router = inject(Router);

  // Allow sign-in page to be accessed without authentication
  if (url === '/sign-in') {
    return true;
  }

  if (authService.isAuthenticated()) {
    return true;
  }

  const toSignIn = () => {
    console.warn(`${label} denied to ${url} - user not authenticated`);
    return router.createUrlTree(['/sign-in'], {
      queryParams: { returnUrl: url },
    });
  };

  if (tokenRefresh.canRefresh()) {
    return tokenRefresh.refresh().pipe(
      map((): boolean | UrlTree => true),
      catchError(() => of(toSignIn()))
    );
  }

  return toSignIn();
}

/**
 * Authentication Guard
 * 
 * Protects routes that require valid JWT token.
 * Redirects to login if token is missing or invalid.
 * Preserves the return URL for redirect after successful login.
 * 
 * Also prevents logged-out users from staying on protected pages.
 */
export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => requireSession(state.url, 'Access');

/**
 * Child Route Authentication Guard
 * 
 * Protects child routes that require valid JWT token.
 * Allows authenticated users to access child routes.
 * Redirects unauthenticated users to login.
 */
export const canActivateChildGuard: CanActivateChildFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => requireSession(state.url, 'Child route access');
