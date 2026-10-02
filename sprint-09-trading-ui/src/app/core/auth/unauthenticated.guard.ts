import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CoreAuthService } from './core-auth.service';

/**
 * Unauthenticated Guard
 * 
 * Prevents authenticated users from accessing login/register pages.
 * If user is logged in, redirects to dashboard.
 */
export const unauthenticatedGuard: CanActivateFn = () => {
  const authService = inject(CoreAuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    // User is already logged in, redirect to dashboard
    router.navigate(['/dashboard']);
    return false;
  }

  return true;
};
