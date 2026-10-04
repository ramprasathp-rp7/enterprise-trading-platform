package com.tradingplatform.exception;

/**
 * Thrown when a duplicate delivery is detected during order settlement.
 * This happens when the guarded state transition finds the order is not in NEW status,
 * indicating another delivery already processed it.
 */
public class DuplicateDeliveryException extends Exception {
    public DuplicateDeliveryException(String message) {
        super(message);
    }

    public DuplicateDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
