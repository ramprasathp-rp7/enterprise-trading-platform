/**
 * Production environment configuration
 * 
 * Override base environment settings for production deployment.
 * This file replaces environment.ts during the production build (ng build --configuration production).
 */

import { baseEnvironment } from './environment.base';

export const environment = {
  ...baseEnvironment,
  
  /**
   * Production Auth Service URL
   * Update this value for your production deployment
   */
  AUTH_SERVICE_URL: 'https://api.trading-platform.example.com/auth',
  
  /**
   * Production Trade API URL  
   * Update this value for your production deployment
   */
  TRADE_API_URL: 'https://api.trading-platform.example.com/trade',
} as const;
