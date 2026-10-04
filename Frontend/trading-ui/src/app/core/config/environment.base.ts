/**
 * Base environment configuration
 * 
 * Shared defaults for all environments.
 * This file is never replaced during builds, so all environment files
 * can safely import from it.
 */

export const baseEnvironment = {
  AUTH_SERVICE_URL: 'http://localhost:3000',
  TRADE_API_URL: 'http://localhost:8080',
  APP_NAME: 'Trading UI',
  APP_VERSION: '1.0.0',
  ENABLE_ANALYTICS: true,
} as const;
