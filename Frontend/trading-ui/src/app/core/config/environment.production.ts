/**
 * Production environment configuration
 *
 * This file replaces environment.ts during the production build (ng build --configuration production).
 * Service URLs come from Config/application.yaml through platform-config.generated.ts, which
 * `npm run build` regenerates first: set services.auth and services.order there for the target
 * environment before building, instead of hardcoding hosts here.
 */

import { baseEnvironment } from './environment.base';

export const environment = {
  ...baseEnvironment,
} as const;
