package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

/**
 * Exception thrown when a preference is invalid (e.g., invalid key, invalid value type, out of range).
 * Maps to HTTP 422 Unprocessable Entity in GlobalExceptionHandler.
 */
public final class InvalidPreferenceException extends DomainException {
    public InvalidPreferenceException(String message) {
        super(ErrorCode.INVALID_PREFERENCE, message);
    }
}
