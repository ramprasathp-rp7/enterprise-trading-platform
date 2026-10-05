import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CoreAuthService } from './core-auth.service';

/**
 * Customer Guard
 * 
 * Protects customer routes by checking if the current user is NOT an admin.
 * Redirects admin users to the admin dashboard.
 * 
 * This prevents admins from accessing customer-only routes like /dashboard, /orders, etc.
 */
export const customerGuard: CanActivateFn = (route, state) => {
  const authService = inject(CoreAuthService);
  const router = inject(Router);

  // If user is admin, redirect to admin dashboard
  if (authService.isAdmin()) {
    console.warn('Access denied: Admin users cannot access customer routes. Redirecting to /admin/analytics');
    router.navigateByUrl('/admin/analytics');
    return false;
  }

  // User is a customer, allow access
  return true;
};
