package com.tradingplatform.domain.exception;
// Account history query parameters (limit, date range) are out of bounds

public final class InvalidHistoryRequestException extends DomainException {

    public InvalidHistoryRequestException(String catalogueMessage) {
        super(com.tradingplatform.domain.enums.ErrorCode.VAL_422, catalogueMessage);
    }
}
