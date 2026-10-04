import { Injectable, signal, computed } from '@angular/core';
import { SignInService } from './sign-in.service';
import { AuthUser, SignInState, getErrorMessage } from './sign-in.models';

/**
 * Sign-in state store
 * Manages sign-in state with Angular signals
 * 
 * Provides:
 * - isLoading signal: true when login is in progress
 * - user signal: current authenticated user
 * - error signal: error message if login failed
 * - errorCode signal: error code from Auth service
 * 
 * Actions:
 * - login(username, password): attempt login
 * - logout(): sign out current user
 * - clearError(): clear error messages
 */
@Injectable({
  providedIn: 'root',
})
export class SignInStore {
  // State signals
  private readonly isLoadingSignal = signal<boolean>(false);
  private readonly userSignal = signal<AuthUser | null>(null);
  private readonly errorSignal = signal<string | null>(null);
  private readonly errorCodeSignal = signal<string | null>(null);

  // Computed signals
  isLoading = computed(() => this.isLoadingSignal());
  user = computed(() => this.userSignal());
  error = computed(() => this.errorSignal());
  errorCode = computed(() => this.errorCodeSignal());

  // Computed readable error message
  errorMessage = computed(() => getErrorMessage(this.errorCodeSignal()));

  constructor(private signInService: SignInService) {}

  /**
   * Attempt to sign in with username and password
   * Returns: true on success, false on failure
   */
  login(username: string, password: string): Promise<boolean> {
    this.isLoadingSignal.set(true);
    this.errorSignal.set(null);
    this.errorCodeSignal.set(null);

    return this.signInService
      .login({ username, password })
      .toPromise()
      .then((user: AuthUser | undefined) => {
        this.userSignal.set(user || null);
        this.isLoadingSignal.set(false);
        return true;
      })
      .catch((error) => {
        // Handle error response
        // Extract errorCode from HttpErrorResponse.error.errorCode
        const errorCode = error?.error?.errorCode || 'UNKNOWN';
        const errorMessage = getErrorMessage(errorCode);

        this.errorCodeSignal.set(errorCode);
        this.errorSignal.set(errorMessage);
        this.isLoadingSignal.set(false);

        return false;
      });
  }

  /**
   * Sign out and clear all state
   */
  logout(): void {
    this.signInService.logout();
    this.userSignal.set(null);
    this.errorSignal.set(null);
    this.errorCodeSignal.set(null);
    this.isLoadingSignal.set(false);
  }

  /**
   * Clear error messages
   */
  clearError(): void {
    this.errorSignal.set(null);
    this.errorCodeSignal.set(null);
  }
}
