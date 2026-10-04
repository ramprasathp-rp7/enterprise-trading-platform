/**
 * Explicit development environment configuration.
 *
 * Used by angular.json fileReplacements for the development build.
 */

import { baseEnvironment } from './environment.base';

export const environment = {
  ...baseEnvironment,
  AUTH_SERVICE_URL: 'http://localhost:3000',
  TRADE_API_URL: 'http://localhost:8080',
} as const;
