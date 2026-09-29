package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

/**
 * Thrown when a transaction request is invalid (e.g., negative amount, invalid account).
 */
public class InvalidTransactionException extends DomainException {

    public InvalidTransactionException(String message) {
        super(ErrorCode.TRANS_400, message);
    }

    public InvalidTransactionException(String message, Throwable cause) {
        super(ErrorCode.TRANS_400, message);
        this.initCause(cause);
    }
}
