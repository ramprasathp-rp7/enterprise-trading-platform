import { Injectable, inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { CoreAuthService } from './core-auth.service';

/**
 * Authentication Guard
 * 
 * Protects routes that require valid JWT token.
 * Redirects to login if token is missing or invalid.
 * Preserves the return URL for redirect after successful login.
 */
export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const authService = inject(CoreAuthService);
  const router = inject(Router);

  // Check if user is authenticated
  if (authService.isAuthenticated()) {
    return true;
  }

  // Redirect to sign-in with return URL
  router.navigate(['/sign-in'], {
    queryParams: { returnUrl: state.url },
  });

  return false;
};
