import { Injectable } from '@angular/core';

/**
 * Sign-in service
 * 
 * Calls:
 * - POST /auth/login
 * - POST /auth/register
 * - POST /auth/refresh
 * - GET /auth/me
 */
@Injectable({
  providedIn: 'root',
})
export class SignInService {
  constructor() {}

  // Service implementation
}
