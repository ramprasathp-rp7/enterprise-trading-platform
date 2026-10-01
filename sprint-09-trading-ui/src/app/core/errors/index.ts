/**
 * Error handling module for Sprint 09 Trading UI.
 *
 * This module provides comprehensive error code handling for both Trade API and Auth API:
 * - Error code enumeration and mapping
 * - User-friendly message rendering
 * - Network error handling
 * - Unknown code fallback
 *
 * Usage:
 *   import { errorCodeCatalogue, errorResponseHandler } from '@app/core/errors';
 *
 *   // Get user-friendly message for error code
 *   const message = errorCodeCatalogue.getMessage('ACC-404');
 *
 *   // Process HTTP error response
 *   const processed = errorResponseHandler.processError(status, body);
 *   console.log(processed.message); // User-friendly message
 */

export * from './error-codes';
export * from './error-response-handler';
export * from './error-alert.component';
