package com.tradingplatform.domain.exception;

import com.tradingplatform.enums.ErrorCode;

/**
 * Thrown when an order cannot be cancelled because it is not in NEW status.
 * Maps to ORD-409 (Conflict).
 */
public final class OrderNotCancellableException extends DomainException {

    private final String orderId;

    public OrderNotCancellableException(String orderId) {
        super(ErrorCode.ORD_409, "Order cannot be cancelled - it is not in NEW status");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
