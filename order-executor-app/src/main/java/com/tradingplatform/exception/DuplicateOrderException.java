package com.tradingplatform.domain.exception;
// Rule 8 : Duplicate Idempotency Key in order

public final class DuplicateOrderException extends DomainException {

    private final String idempotencyKey;

    public DuplicateOrderException(String idempotencyKey) {
        super(com.tradingplatform.domain.enums.ErrorCode.ORD_409, "Duplicate order");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
