/**
 * Base environment configuration
 * 
 * Shared defaults for all environments.
 * This file is never replaced during builds, so all environment files
 * can safely import from it.
 *
 * Service URLs come from Config/application.yaml via platform-config.generated.ts.
 */

import { PLATFORM_URLS } from './platform-config.generated';

export const baseEnvironment = {
  AUTH_SERVICE_URL: PLATFORM_URLS.AUTH_SERVICE_URL,
  TRADE_API_URL: PLATFORM_URLS.TRADE_API_URL,
  APP_NAME: 'Trading UI',
  APP_VERSION: '1.0.0',
  ENABLE_ANALYTICS: true,
} as const;
