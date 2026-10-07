package com.tradingplatform.strategy.client;

/** The Trade API did not accept a strategy's order: a rejection (4xx) or an unreachable/failing API */
public class OrderPlacementException extends RuntimeException {

    private final String errorCode;

    public OrderPlacementException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** The API's error code (e.g. ORD-400, VAL-422), or UNAVAILABLE when no answer came back */
    public String getErrorCode() {
        return errorCode;
    }
}
