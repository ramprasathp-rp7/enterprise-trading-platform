import { inject } from '@angular/core';
import { CanActivateFn, CanActivateChildFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { CoreAuthService } from './core-auth.service';

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
) => {
  const authService = inject(CoreAuthService);
  const router = inject(Router);

  // Allow sign-in page to be accessed without authentication
  if (state.url === '/sign-in') {
    return true;
  }

  // Check if user is authenticated
  const isAuth = authService.isAuthenticated();
  
  if (!isAuth) {
    // Redirect to login with return URL
    console.warn(`Access denied to ${state.url} - user not authenticated`);
    return router.createUrlTree(['/sign-in'], {
      queryParams: { returnUrl: state.url },
    });
  }

  return true;
};

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
) => {
  const authService = inject(CoreAuthService);
  const router = inject(Router);

  // Allow sign-in page to be accessed without authentication
  if (state.url === '/sign-in') {
    return true;
  }

  // Check if user is authenticated
  const isAuth = authService.isAuthenticated();
  
  if (!isAuth) {
    // Redirect to login with return URL
    console.warn(`Child route access denied to ${state.url} - user not authenticated`);
    return router.createUrlTree(['/sign-in'], {
      queryParams: { returnUrl: state.url },
    });
  }

  return true;
};