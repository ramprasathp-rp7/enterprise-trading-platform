import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CoreAuthService } from './core-auth.service';

/**
 * Admin Guard
 * 
 * Protects admin routes by checking if the current user has the ADMIN role.
 * Redirects non-admin users to the customer dashboard.
 */
export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(CoreAuthService);
  const router = inject(Router);

  if (authService.isAdmin()) {
    return true;
  }

  // Not admin, redirect to customer dashboard
  console.warn('Access denied: User does not have ADMIN role. Redirecting to /dashboard');
  router.navigateByUrl('/dashboard');
  return false;
};
