import { Injectable, signal, computed } from '@angular/core';
import { JWTClaims, JWT, AuthState } from './auth.models';

/**
 * Core Authentication Service
 * 
 * Manages application-wide authentication state and JWT token handling.
 * Persists tokens to sessionStorage and provides signals for reactive state.
 */
@Injectable({
  providedIn: 'root',
})
export class CoreAuthService {
  private readonly ACCESS_TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';

  // Signals for authentication state
  private readonly accessTokenSignal = signal<string | null>(this.loadAccessToken());
  private readonly refreshTokenSignal = signal<string | null>(this.loadRefreshToken());
  private readonly userSignal = signal<AuthState['user']>(null);
  private readonly errorSignal = signal<string | null>(null);
  private readonly errorCodeSignal = signal<string | null>(null);

  // Computed signals
  isAuthenticated = computed(() => {
    const token = this.accessTokenSignal();
    if (!token) return false;
    // Check if token is expired
    try {
      const claims = this.decodeJWT(token);
      return !this.isTokenExpired(claims);
    } catch {
      return false;
    }
  });

  currentUser = computed(() => this.userSignal());
  error = computed(() => this.errorSignal());
  errorCode = computed(() => this.errorCodeSignal());
  accessToken = computed(() => this.accessTokenSignal());
  refreshToken = computed(() => this.refreshTokenSignal());

  constructor() {
    // If token exists in sessionStorage, extract user info from it
    const accessToken = this.loadAccessToken();
    if (accessToken) {
      try {
        const claims = this.decodeJWT(accessToken);
        // Extract user info from JWT claims
        // Try to get username from: username, email prefix, or name field
        const username = (claims as any).username || 
                        (claims as any).email?.split('@')[0] ||
                        (claims as any).name ||
                        'Trader';
        
        const user: AuthState['user'] = {
          id: claims.sub,
          username: username,
          accountId: claims.accountId,
          roles: claims.roles
        };
        this.userSignal.set(user);
      } catch (error) {
        console.error('Failed to decode JWT on init:', error);
      }
    }
  }

  /**
   * Store tokens and user info after successful login
   */
  setTokens(
    accessToken: string,
    refreshToken: string,
    user: AuthState['user'],
    expiresIn?: number
  ): void {
    // Ensure user has a username fallback
    if (user) {
      user.username = user.username || 'Trader';
    }
    
    this.accessTokenSignal.set(accessToken);
    this.refreshTokenSignal.set(refreshToken);
    this.userSignal.set(user);
    this.errorSignal.set(null);
    this.errorCodeSignal.set(null);

    // Store in sessionStorage for persistence
    sessionStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    sessionStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
  }

  /**
   * Clear all tokens and authentication state
   */
  clearTokens(): void {
    this.accessTokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    this.userSignal.set(null);
    this.errorSignal.set(null);
    this.errorCodeSignal.set(null);

    // Remove from sessionStorage
    sessionStorage.removeItem(this.ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  /**
   * Set error state
   */
  setError(message: string, errorCode: string | null = null): void {
    this.errorSignal.set(message);
    this.errorCodeSignal.set(errorCode);
  }

  /**
   * Clear error state
   */
  clearError(): void {
    this.errorSignal.set(null);
    this.errorCodeSignal.set(null);
  }

  /**
   * Update user info
   */
  setUser(user: AuthState['user']): void {
    this.userSignal.set(user);
  }

  /**
   * Decode JWT payload (does not validate signature - should only be used with server-validated tokens)
   */
  decodeJWT(token: string): JWTClaims {
    try {
      // JWT format: header.payload.signature
      const parts = token.split('.');
      if (parts.length !== 3) {
        throw new Error('Invalid JWT format');
      }

      // Decode the payload (second part)
      const decoded = JSON.parse(atob(parts[1]));
      return decoded as JWTClaims;
    } catch (error) {
      throw new Error(`Failed to decode JWT: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * Check if token is expired
   */
  isTokenExpired(claims: JWTClaims): boolean {
    const now = Math.floor(Date.now() / 1000);
    return claims.exp < now;
  }

  /**
   * Get seconds until token expires
   */
  getSecondsUntilExpiry(token: string): number | null {
    try {
      const claims = this.decodeJWT(token);
      const now = Math.floor(Date.now() / 1000);
      return Math.max(0, claims.exp - now);
    } catch {
      return null;
    }
  }

  /**
   * Check if token needs refresh (e.g., less than 1 minute remaining)
   */
  shouldRefreshToken(token: string): boolean {
    const secondsUntilExpiry = this.getSecondsUntilExpiry(token);
    return secondsUntilExpiry !== null && secondsUntilExpiry < 60;
  }

  /**
   * Load access token from sessionStorage
   */
  private loadAccessToken(): string | null {
    return sessionStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  /**
   * Load refresh token from sessionStorage
   */
  private loadRefreshToken(): string | null {
    return sessionStorage.getItem(this.REFRESH_TOKEN_KEY);
  }
}
