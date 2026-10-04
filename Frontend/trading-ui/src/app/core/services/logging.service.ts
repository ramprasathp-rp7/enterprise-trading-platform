import { Injectable } from '@angular/core';

/**
 * Logging service
 * 
 * Centralized logging for debugging and monitoring.
 * Never logs sensitive information (tokens, credentials, PII).
 */
@Injectable({
  providedIn: 'root',
})
export class LoggingService {
  constructor() {}

  debug(message: string, data?: any): void {}
  info(message: string, data?: any): void {}
  warn(message: string, data?: any): void {}
  error(message: string, data?: any): void {}
}
