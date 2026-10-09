package com.tradingplatform.domain.exception;

// The market data provider (Fauxnance) could not be reached or gave an unusable answer

public final class MarketDataUnavailableException extends DomainException {

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(com.tradingplatform.domain.enums.ErrorCode.MKT_503, message);
        initCause(cause);
    }
}
