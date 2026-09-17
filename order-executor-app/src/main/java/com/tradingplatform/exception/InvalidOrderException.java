package com.tradingplatform.exception;
// Rule 4, 5 : Quantity and Price must be positive

import com.tradingplatform.enums.ErrorCode;

public final class InvalidOrderException extends DomainException {

    public InvalidOrderException(String catalogueMessage) {
        super(ErrorCode.VAL_422, catalogueMessage);
    }
}
