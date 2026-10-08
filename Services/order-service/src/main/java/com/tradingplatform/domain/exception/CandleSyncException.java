package com.tradingplatform.domain.exception;

public final class CandleSyncException extends CandleDataException {

    public CandleSyncException(String message) {
        super(message);
    }

    public CandleSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
