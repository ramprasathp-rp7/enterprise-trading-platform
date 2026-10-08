/**
 * Explicit development environment configuration.
 *
 * Used by angular.json fileReplacements for the development build.
 */

import { baseEnvironment } from './environment.base';

export const environment = {
  ...baseEnvironment,
} as const;
