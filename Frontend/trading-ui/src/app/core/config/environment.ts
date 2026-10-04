/**
 * Development environment configuration
 * 
 * This is the default configuration used during `ng serve` and `npm start`.
 * For production builds, Angular replaces this entire file with environment.production.ts
 */

import { baseEnvironment } from './environment.base';

export const environment = {
  ...baseEnvironment,
  
  /**
   * Development Auth Service URL
   * Points to local Auth Service instance
   */
  AUTH_SERVICE_URL: 'http://localhost:3000',
  
  /**
   * Development Trade API URL
   * Event-backbone serves the `/api/v1/*` trade endpoints in local development.
   * Points to local Trade API instance
   */
  TRADE_API_URL: 'http://localhost:8080',
} as const;
