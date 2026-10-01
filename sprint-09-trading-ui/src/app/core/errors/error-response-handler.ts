import { errorCodeCatalogue } from './error-codes';

/**
 * Structure of error responses from Trade API and Auth API.
 * Both contracts return: { "errorCode": "...", "message": "..." }
 */
export interface ApiErrorResponse {
  errorCode?: string;
  message?: string;
}

/**
 * Processed error information for display to user.
 * Contains the error code and user-friendly message.
 */
export interface ProcessedError {
  code: string;
  message: string;
  isNetworkError: boolean;  // true when status is 0
  isUnknownCode: boolean;   // true when code not in catalogue
}

/**
 * Handles error responses from API calls.
 * Processes various error scenarios and returns user-friendly messages.
 */
export class ErrorResponseHandler {
  /**
   * Processes an HTTP error response or network error.
   * Handles:
   * - Normal API errors with errorCode and message
   * - Network errors (status 0)
   * - Unknown error codes
   * - Malformed responses
   *
   * @param status - HTTP status code (0 for network errors)
   * @param errorBody - The response body
   * @returns ProcessedError with code and user-friendly message
   */
  processError(status: number, errorBody?: any): ProcessedError {
    // Handle network errors: status 0 means service down or CORS issue
    if (status === 0 || !errorBody) {
      return {
        code: 'NETWORK_ERROR',
        message: 'Service is unavailable. Check your connection or try again later.',
        isNetworkError: true,
        isUnknownCode: false,
      };
    }

    // Parse error response
    const apiError = this.parseErrorResponse(errorBody);
    const code = apiError.errorCode || 'UNKNOWN_ERROR';

    // Get user-friendly message
    const message = errorCodeCatalogue.getMessage(code);
    const isUnknownCode = !errorCodeCatalogue.isKnownCode(code);

    return {
      code,
      message,
      isNetworkError: false,
      isUnknownCode,
    };
  }

  /**
   * Safely parses an error response from various formats.
   * Handles JSON strings, objects, and malformed responses.
   *
   * @param errorBody - The error response body
   * @returns Parsed ApiErrorResponse
   */
  private parseErrorResponse(errorBody: any): ApiErrorResponse {
    if (!errorBody) {
      return {};
    }

    // If it's already an object with errorCode, return it
    if (typeof errorBody === 'object' && 'errorCode' in errorBody) {
      return errorBody as ApiErrorResponse;
    }

    // Try to parse as JSON string
    if (typeof errorBody === 'string') {
      try {
        const parsed = JSON.parse(errorBody);
        if (typeof parsed === 'object' && 'errorCode' in parsed) {
          return parsed as ApiErrorResponse;
        }
      } catch {
        // Not valid JSON, continue
      }
    }

    // Unable to parse, return empty response
    return {};
  }

  /**
   * Checks if an error is a specific error code.
   * Useful for conditional logic in components.
   *
   * @param processedError - The processed error
   * @param code - The error code to check
   * @returns true if error matches the code
   */
  isErrorCode(processedError: ProcessedError, code: string): boolean {
    return processedError.code === code;
  }

  /**
   * Checks if an error is a network/connectivity issue.
   *
   * @param processedError - The processed error
   * @returns true if error is network-related
   */
  isNetworkError(processedError: ProcessedError): boolean {
    return processedError.isNetworkError;
  }

  /**
   * Checks if an error code was not found in the catalogue.
   * Indicates the application may need an update.
   *
   * @param processedError - The processed error
   * @returns true if error code is unknown
   */
  isUnknownCode(processedError: ProcessedError): boolean {
    return processedError.isUnknownCode;
  }
}

/**
 * Singleton instance of the error response handler.
 */
export const errorResponseHandler = new ErrorResponseHandler();
