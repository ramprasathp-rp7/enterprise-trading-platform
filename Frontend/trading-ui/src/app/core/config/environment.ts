/**
 * Development environment configuration
 * 
 * This is the default configuration used during `ng serve` and `npm start`.
 * For production builds, Angular replaces this entire file with environment.production.ts
 */

import { baseEnvironment } from './environment.base';

export const environment = {
  ...baseEnvironment,
  
  // AUTH_SERVICE_URL and TRADE_API_URL come from baseEnvironment, which reads
  // Config/application.yaml (services.auth / services.order) at build time.
} as const;
