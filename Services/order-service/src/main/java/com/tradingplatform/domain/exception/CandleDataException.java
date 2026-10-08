package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

public class CandleDataException extends DomainException {

    public CandleDataException(String message) {
        super(ErrorCode.VAL_422, message);
    }

    public CandleDataException(String message, Throwable cause) {
        super(ErrorCode.VAL_422, message);
        initCause(cause);
    }
}
