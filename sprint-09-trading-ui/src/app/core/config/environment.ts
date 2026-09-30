/**
 * Application configuration
 * 
 * Environment-specific settings:
 * - API base URLs (auth-service, trade-api)
 * - JWT settings (secret, algorithm, lifetime)
 * - Feature flags
 * - Logging levels
 * 
 * For environment-specific overrides, use Angular's ng build --configuration option
 * with environment files (e.g., environment.production.ts)
 */

export const environment = {
  /**
   * Auth Service base URL
   * Default: http://localhost:3000
   * Can be overridden in environment-specific files
   */
  AUTH_SERVICE_URL: 'http://localhost:3000',

  /**
   * Trade API base URL
   * Default: http://localhost:4000
   * Can be overridden in environment-specific files
   */
  TRADE_API_URL: 'http://localhost:4000',

  /**
   * Application name and version
   */
  APP_NAME: 'Trading UI',
  APP_VERSION: '1.0.0',

  /**
   * Feature flags
   */
  ENABLE_ANALYTICS: true,
};
