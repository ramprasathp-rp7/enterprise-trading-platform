/**
 * Sign-in feature types and interfaces
 */

import { UserResponse } from '../../api/generated/auth';

/**
 * Current user info from auth service
 */
export type AuthUser = UserResponse;

/**
 * Login request model (maps to generated LoginRequest)
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Sign-in state model
 */
export interface SignInState {
  /**
   * Whether a sign-in operation is in progress
   */
  isLoading: boolean;

  /**
   * Current authenticated user if signed in
   */
  user: AuthUser | null;

  /**
   * Error message if sign-in failed
   */
  error: string | null;

  /**
   * Error code from the Auth service
   */
  errorCode: string | null;
}

/**
 * Error code to readable message mapping
 */
export const ERROR_CODE_MESSAGES: Record<string, string> = {
  'AUTH-401': 'The username or password is incorrect',
  'AUTH-409': 'That username is already taken',
  'VAL-422': 'Please check your input',
};

/**
 * Get readable error message for error code
 * Returns null if no error code is provided (no error)
 * Returns a user-friendly message for known codes
 * Returns a fallback message with the error code for unknown codes
 */
export function getErrorMessage(errorCode: string | null): string | null {
  if (!errorCode) {
    // No error code = no error, return null (nothing to display)
    return null;
  }
  // Return mapped message or fallback with code
  return ERROR_CODE_MESSAGES[errorCode] || `An error occurred (${errorCode})`;
}
