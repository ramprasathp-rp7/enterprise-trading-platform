import { Injectable } from '@angular/core';
import { Observable, throwError, of } from 'rxjs';
import { map, catchError, tap, switchMap } from 'rxjs/operators';
import { AuthService, LoginRequest, TokenResponse, UserResponse, ProfileService } from '../../api/generated/auth';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { LoginRequest as LocalLoginRequest } from './sign-in.models';

/**
 * Sign-in service
 * 
 * Calls:
 * - POST /auth/login
 * - POST /auth/register
 * - POST /auth/refresh
 * - GET /auth/me
 * 
 * Integrates with CoreAuthService for token storage and management.
 */
@Injectable({
  providedIn: 'root',
})
export class SignInService {
  constructor(
    private authApi: AuthService,
    private profileApi: ProfileService,
    private coreAuthService: CoreAuthService
  ) {}

  /**
   * Attempt login with username and password
   * Stores tokens if successful
   * 
   * Note: We extract user info from JWT claims during tap().
   * The subsequent getCurrentUser() call is optional - if it fails (401, network error, etc),
   * we still consider login successful and return the user info from the JWT.
   */
  login(credentials: LocalLoginRequest): Observable<UserResponse> {
    const loginRequest: LoginRequest = {
      username: credentials.username,
      password: credentials.password,
    };

    let storedUser: UserResponse | null = null;

    return this.authApi.login(loginRequest).pipe(
      tap((response: TokenResponse) => {
        // Store tokens in CoreAuthService
        // Extract user info from token claims
        try {
          const claims = this.coreAuthService.decodeJWT(response.accessToken);
          storedUser = {
            id: claims.sub,
            username: loginRequest.username,
            accountId: claims.accountId,
            roles: this.coreAuthService.extractRolesFromClaims(claims) as any[],
            createdOn: new Date().toISOString(),
          };
          this.coreAuthService.setTokens(
            response.accessToken,
            response.refreshToken,
            storedUser,
            response.expiresIn
          );
        } catch (error) {
          // If we can't decode the token, still store it but without user info
          this.coreAuthService.setTokens(
            response.accessToken,
            response.refreshToken,
            null,
            response.expiresIn
          );
        }
      }),
      // Try to fetch full user info, but don't fail login if it errors
      switchMap(() => 
        this.getCurrentUser().pipe(
          catchError((error) => {
            // If getCurrentUser fails (e.g., 401 on /auth/me), just return stored user
            // The login itself was successful and tokens are stored
            if (storedUser) {
              return of(storedUser);
            }
            // If we don't have stored user info, propagate the error
            return throwError(() => error);
          })
        )
      ),
      catchError((error) => {
        // Only clear tokens if login endpoint itself failed, not if getCurrentUser failed
        // This is already handled in the switchMap catchError above
        return throwError(() => error);
      })
    );
  }

  /**
   * Get current authenticated user
   */
  getCurrentUser(): Observable<UserResponse> {
    return this.profileApi.getCurrentUser().pipe(
      tap((user: UserResponse) => {
        // Update user info in CoreAuthService
        this.coreAuthService.setUser({
          id: user.id,
          username: user.username,
          accountId: user.accountId,
          roles: user.roles,
        });
      }),
      catchError((error) => {
        return throwError(() => error);
      })
    );
  }

  /**
   * Logout and clear tokens
   */
  logout(): void {
    this.coreAuthService.clearTokens();
  }

  /**
   * Refresh the access token using the refresh token
   */
  refreshToken(): Observable<TokenResponse> {
    const refreshToken = this.coreAuthService.refreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }

    return this.authApi.refresh({ refreshToken }).pipe(
      tap((response: TokenResponse) => {
        // Store new tokens
        this.coreAuthService.setTokens(
          response.accessToken,
          response.refreshToken,
          this.coreAuthService.currentUser(),
          response.expiresIn
        );
      }),
      catchError((error) => {
        // Clear tokens on refresh failure
        this.coreAuthService.clearTokens();
        return throwError(() => error);
      })
    );
  }
}
