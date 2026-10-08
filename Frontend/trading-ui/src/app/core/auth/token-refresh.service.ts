import { Injectable, inject } from '@angular/core';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { finalize, map, shareReplay, tap } from 'rxjs/operators';
import { CoreAuthService } from './core-auth.service';
import { environment } from '../config/environment';
import { AuthService, BASE_PATH, Configuration } from '../../api/generated/auth';

/** Refresh this many seconds before the access token actually expires. */
const EXPIRY_LEEWAY_SECONDS = 30;

/**
 * Exchanges the stored refresh token for a new access/refresh token pair
 * via POST /auth/refresh, through the generated Auth API client
 * (contracts/auth-api.yaml).
 *
 * - The client is built on HttpBackend, so the call bypasses the interceptors
 *   (no recursion, no circular DI with AuthInterceptor).
 * - Single flight: concurrent callers share one request. The auth service
 *   rotates refresh tokens and revokes the whole session if a used token is
 *   replayed, so two parallel refreshes would sign the user out.
 */
@Injectable({ providedIn: 'root' })
export class TokenRefreshService {
  private readonly authService = inject(CoreAuthService);
  /**
   * The generated Auth client on a backend-only HttpClient. It is constructed here, not injected,
   * because an injected one would send /auth/refresh back through AuthInterceptor, which itself
   * depends on this service.
   */
  private readonly authApi = new AuthService(
    new HttpClient(inject(HttpBackend)),
    inject(BASE_PATH, { optional: true }) ?? environment.AUTH_SERVICE_URL,
    inject(Configuration, { optional: true }) ?? undefined,
  );

  private inFlight: Observable<string> | null = null;

  /** True when a refresh token is stored. */
  canRefresh(): boolean {
    return !!this.authService.refreshToken();
  }

  /** True when the access token is missing, unreadable, or (nearly) expired. */
  needsRefresh(accessToken: string | null): boolean {
    if (!accessToken) {
      return true;
    }
    const secondsLeft = this.authService.getSecondsUntilExpiry(accessToken);
    return secondsLeft === null || secondsLeft <= EXPIRY_LEEWAY_SECONDS;
  }

  /**
   * Get a fresh access token. Emits the new token, or errors (after clearing
   * the session) when the refresh token is missing, expired or revoked.
   */
  refresh(): Observable<string> {
    if (this.inFlight) {
      return this.inFlight;
    }

    const refreshToken = this.authService.refreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }

    this.inFlight = this.authApi.refresh({ refreshToken }).pipe(
      tap({
        next: (tokens) =>
          this.authService.setTokens(tokens.accessToken, tokens.refreshToken, this.authService.currentUser(), tokens.expiresIn),
        error: () => this.authService.clearTokens(),
      }),
      map((tokens) => tokens.accessToken),
      finalize(() => (this.inFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.inFlight;
  }
}
