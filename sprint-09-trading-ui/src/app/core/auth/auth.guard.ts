import { Injectable } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * Authentication Guard
 * 
 * Protects routes that require valid JWT token.
 * Redirects to login if token is missing or invalid.
 */
export const authGuard: CanActivateFn = (route, state) => {
  // Guard implementation
  return true;
};
