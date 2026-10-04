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
  private readonly USERNAME_KEY = 'auth_username';

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
        // Extract user info from JWT claims without forcing generic role labels as username.
        const username = this.resolveUsernameFromClaims(claims) || this.loadStoredUsername() || 'User';
        
        const user: AuthState['user'] = {
          id: claims.sub,
          username: username,
          accountId: claims.accountId,
          roles: this.resolveRolesFromClaims(claims)
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
    this.accessTokenSignal.set(accessToken);
    this.refreshTokenSignal.set(refreshToken);
    if (user) {
      const resolvedUsername = user.username || this.loadStoredUsername() || this.resolveUsernameFromAccessToken(accessToken) || 'User';
      const normalizedRoles = (user.roles || [])
        .filter((role): role is string => typeof role === 'string')
        .map((role) => this.normalizeRole(role))
        .filter((role) => role.length > 0);

      this.userSignal.set({
        ...user,
        username: resolvedUsername,
        roles: normalizedRoles
      });
      this.storeUsername(resolvedUsername);
    } else {
      this.userSignal.set(user);
    }
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
    sessionStorage.removeItem(this.USERNAME_KEY);
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
    if (!user) {
      this.userSignal.set(user);
      return;
    }

    const normalizedRoles = (user.roles || [])
      .filter((role): role is string => typeof role === 'string')
      .map((role) => this.normalizeRole(role))
      .filter((role) => role.length > 0);

    this.userSignal.set({
      ...user,
      roles: normalizedRoles,
    });
  }

  /**
   * Extract and normalize role names from JWT claims.
   */
  extractRolesFromClaims(claims: JWTClaims): string[] {
    return this.resolveRolesFromClaims(claims);
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

  private loadStoredUsername(): string | null {
    return sessionStorage.getItem(this.USERNAME_KEY);
  }

  private storeUsername(username: string | null): void {
    if (!username || !username.trim()) {
      sessionStorage.removeItem(this.USERNAME_KEY);
      return;
    }

    sessionStorage.setItem(this.USERNAME_KEY, username.trim());
  }

  private resolveUsernameFromAccessToken(accessToken: string | null): string | null {
    if (!accessToken) {
      return null;
    }

    try {
      const claims = this.decodeJWT(accessToken);
      return this.resolveUsernameFromClaims(claims);
    } catch {
      return null;
    }
  }

  /**
   * Get the primary role of the current user
   */
  getUserRole(): string | null {
    const user = this.currentUser();
    if (!user || !user.roles || user.roles.length === 0) {
      return null;
    }
    return this.normalizeRole(user.roles[0]);
  }

  /**
   * Check if current user has a specific role
   */
  hasRole(role: string): boolean {
    const user = this.currentUser();
    if (!user || !user.roles) {
      return false;
    }
    const expectedRole = this.normalizeRole(role);
    return user.roles.some((existingRole) => this.normalizeRole(existingRole) === expectedRole);
  }

  /**
   * Check if current user is an admin
   */
  isAdmin(): boolean {
    return this.hasRole('ADMIN');
  }

  /**
   * Get dashboard route based on user role
   */
  getDashboardRoute(): string {
    return this.isAdmin() ? '/admin/analytics' : '/dashboard';
  }

  private resolveUsernameFromClaims(claims: JWTClaims): string | null {
    const rawUsername = (claims as any).username;
    const preferredUsername = (claims as any).preferred_username;
    const emailPrefix = (claims as any).email?.split('@')[0];
    const name = (claims as any).name;

    const candidates = [rawUsername, preferredUsername, emailPrefix, name]
      .filter((candidate): candidate is string => !!candidate && candidate.trim().length > 0)
      .map((candidate) => candidate.trim());

    const nonGeneric = candidates.find((candidate) => !/^trader$/i.test(candidate));
    return nonGeneric || null;
  }

  private resolveRolesFromClaims(claims: JWTClaims): string[] {
    const directRoles = this.collectRolesFromSource((claims as any).roles);
    const roleClaim = this.collectRolesFromSource((claims as any).role);
    const authorities = this.collectRolesFromSource((claims as any).authorities);
    const scope = this.collectRolesFromSource((claims as any).scope);

    const merged = [...directRoles, ...roleClaim, ...authorities, ...scope]
      .map((role) => this.normalizeRole(role))
      .filter((role) => role.length > 0);

    return Array.from(new Set(merged));
  }

  private collectRolesFromSource(source: unknown): string[] {
    if (Array.isArray(source)) {
      return source.filter((item): item is string => typeof item === 'string');
    }

    if (typeof source === 'string') {
      return source
        .split(/[\s,]+/)
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
    }

    return [];
  }

  private normalizeRole(role: string): string {
    return role.trim().toUpperCase().replace(/^ROLE_/, '');
  }
}
