import { Injectable } from '@angular/core';

/**
 * Error handling service
 * 
 * Centralizes error handling, mapping API error codes to user messages.
 * Handles AUTH-401, ACC-403, VAL-422, ORD-409, etc.
 */
@Injectable({
  providedIn: 'root',
})
export class ErrorHandlingService {
  constructor() {}

  // Error handling implementation
}
