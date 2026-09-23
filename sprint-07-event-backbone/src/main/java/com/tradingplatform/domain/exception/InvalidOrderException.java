package com.tradingplatform.domain.exception;
// Rule 4, 5 : Quantity and Price must be positive

public final class InvalidOrderException extends DomainException {

    public InvalidOrderException(String catalogueMessage) {
        super(com.tradingplatform.domain.enums.ErrorCode.VAL_422, catalogueMessage);
    }
}
