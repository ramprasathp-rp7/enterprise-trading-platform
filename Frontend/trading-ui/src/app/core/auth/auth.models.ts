/**
 * Core authentication types
 */

/**
 * JWT Claims from the Auth Service
 * Based on auth-api.yaml contract section: JWT claims contract
 */
export interface JWTClaims {
  /**
   * The user identifier. A UUID. Stable for the life of the user.
   */
  sub: string;

  /**
   * The numeric trading account key, ACCOUNTS.id.
   * The Trade REST API compares this against the account in the request path or body.
   */
  accountId: number;

  /**
   * Authorisation roles. CUSTOMER for a normal trading user, ADMIN for an operator.
   * Always present, never empty.
   */
  roles: string[];

  /**
   * Issued-at, seconds since the Unix epoch.
   */
  iat: number;

  /**
   * Expiry, seconds since the Unix epoch.
   * Fifteen minutes after iat for an access token.
   */
  exp: number;

  /**
   * Issuer. Expected to be 'auth-service' for the Sprint 8 service.
   */
  iss: string;

  /**
   * Additional claims that may be present in the token.
   */
  [key: string]: any;
}

/**
 * Decoded JWT with its token string
 */
export interface JWT {
  /**
   * The raw JWT string
   */
  token: string;

  /**
   * The decoded claims from the JWT
   */
  claims: JWTClaims;
}

/**
 * Login credentials
 */
export interface LoginCredentials {
  username: string;
  password: string;
}

/**
 * Current authentication state
 */
export interface AuthState {
  /**
   * Whether user is currently authenticated
   */
  isAuthenticated: boolean;

  /**
   * Current user info if authenticated
   */
  user: {
    id: string;
    username: string;
    accountId: number;
    roles: string[];
  } | null;

  /**
   * Current access token if authenticated
   */
  accessToken: string | null;

  /**
   * Current refresh token if authenticated
   */
  refreshToken: string | null;

  /**
   * Token expiration time (Unix timestamp in seconds)
   */
  expiresAt: number | null;

  /**
   * Any error message from authentication attempt
   */
  error: string | null;

  /**
   * Error code from authentication failure
   */
  errorCode: string | null;
}
