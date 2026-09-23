package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.model.Order;

public abstract class DomainException extends RuntimeException {

    private final com.tradingplatform.domain.enums.ErrorCode errorCode;
    private Order rejectedOrder;

    protected DomainException(com.tradingplatform.domain.enums.ErrorCode errorCode, String catalogueMessage) {
        super(catalogueMessage);
        this.errorCode = errorCode;
    }

    public com.tradingplatform.domain.enums.ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.code();
    }

    /**
     * Attaches the order this exception rejected, so a caller (e.g. the Sprint 6 controller) can
     * persist it to the audit trail as REJECTED.
     *
     * <p>Only set for rules that reject an already-well-formed order (insufficient funds,
     * insufficient holdings, duplicate idempotency key). Rules that fail before a valid Order can
     * even be constructed — unknown account/instrument, non-positive quantity/price — leave this
     * null: Order's own constructor enforces those same invariants, so there is no order to have
     * rejected, only a request that never became one.</p>
     */
    public DomainException withRejectedOrder(Order order) {
        this.rejectedOrder = order;
        return this;
    }

    public Order getRejectedOrder() {
        return rejectedOrder;
    }
}