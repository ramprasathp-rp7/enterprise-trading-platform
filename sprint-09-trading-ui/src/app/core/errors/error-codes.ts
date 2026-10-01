/**
 * Error codes from Trade API and Auth API contracts.
 * Used to branch on error type and render user-actionable messages.
 */
export enum ErrorCode {
  // Trade API error codes
  ACC_404 = 'ACC-404',          // Account not found
  ACC_403 = 'ACC-403',          // Account not active or not reachable with token
  INS_404 = 'INS-404',          // Instrument not found or not tradable
  ORD_400 = 'ORD-400',          // Insufficient funds
  ORD_409 = 'ORD-409',          // Insufficient holdings, duplicate order, or order not cancellable
  TRANS_404 = 'TRANS-404',      // Transaction not found
  TRANS_400 = 'TRANS-400',      // Invalid transaction or insufficient funds for withdrawal
  VAL_422 = 'VAL-422',          // Invalid input
  AUTH_401 = 'AUTH-401',        // Unauthorised or invalid token

  // Auth API specific error codes
  AUTH_409 = 'AUTH-409',        // Username already taken
}

/**
 * User-friendly message mapping for each error code.
 * These are actionable sentences for traders, not developer-facing messages.
 */
export const ERROR_CODE_MESSAGES: Record<ErrorCode, string> = {
  [ErrorCode.ACC_404]: 'The account could not be found. Please verify your account ID or contact support.',
  [ErrorCode.ACC_403]: 'This account is not active, or is not the one this sign-in may trade. Please verify your credentials.',
  [ErrorCode.INS_404]: 'The instrument is not one that can be traded. Please select a different security.',
  [ErrorCode.ORD_400]: 'There is not enough cash for this order. Please deposit funds or reduce the order size.',
  [ErrorCode.ORD_409]: 'Not enough of the holding to sell, or this order has already been placed. Please check your positions and try again.',
  [ErrorCode.TRANS_404]: 'The transaction could not be found. Please verify the transaction ID or check your history.',
  [ErrorCode.TRANS_400]: 'Insufficient funds for this withdrawal. Please check your available balance.',
  [ErrorCode.VAL_422]: 'A field is not acceptable. Please review your input and try again.',
  [ErrorCode.AUTH_401]: 'The session has expired or the sign-in was refused. Please sign in again.',
  [ErrorCode.AUTH_409]: 'That username is already taken. Please choose a different username.',
};

/**
 * Error code catalogue with completeness tracking.
 * Provides validation that all codes are mapped and fallback messages for unknown codes.
 */
export class ErrorCodeCatalogue {
  private readonly allCodes = new Set(Object.values(ErrorCode));
  private readonly mappedCodes = new Set(Object.keys(ERROR_CODE_MESSAGES) as ErrorCode[]);

  /**
   * Gets the user-friendly message for an error code.
   * Returns a fallback message if the code is unknown.
   *
   * @param code - The error code from API response
   * @returns User-actionable message
   */
  getMessage(code: string | null | undefined): string {
    if (!code) {
      return 'An unexpected error occurred. Please try again or contact support.';
    }

    const errorCode = code as ErrorCode;
    if (this.mappedCodes.has(errorCode)) {
      return ERROR_CODE_MESSAGES[errorCode];
    }

    // Fallback for unknown error codes
    return `An unexpected error occurred (${code}). Please try again or contact support.`;
  }

  /**
   * Checks if an error code is known and mapped.
   *
   * @param code - The error code to check
   * @returns true if code is in the catalogue
   */
  isKnownCode(code: string): boolean {
    return this.mappedCodes.has(code as ErrorCode);
  }

  /**
   * Returns all known error codes.
   *
   * @returns Set of all error codes in the catalogue
   */
  getAllCodes(): Set<ErrorCode> {
    return new Set(this.allCodes);
  }

  /**
   * Validates completeness of the error code mapping.
   * Ensures all enum codes have messages defined.
   *
   * @returns Object with validation result and details
   */
  validateCompleteness(): {
    isComplete: boolean;
    totalCodes: number;
    mappedCount: number;
    unmappedCodes: ErrorCode[];
  } {
    const unmappedCodes = Array.from(this.allCodes).filter(
      (code) => !this.mappedCodes.has(code as ErrorCode)
    ) as ErrorCode[];

    return {
      isComplete: unmappedCodes.length === 0,
      totalCodes: this.allCodes.size,
      mappedCount: this.mappedCodes.size,
      unmappedCodes,
    };
  }

  /**
   * Gets a detailed mapping of all error codes to messages.
   * Useful for documentation and verification.
   *
   * @returns Map of error codes to messages
   */
  getFullMapping(): Record<ErrorCode, string> {
    return { ...ERROR_CODE_MESSAGES };
  }
}

/**
 * Singleton instance of the error code catalogue.
 */
export const errorCodeCatalogue = new ErrorCodeCatalogue();
