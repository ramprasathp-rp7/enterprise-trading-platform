package com.tradingplatform.domain.exception;

import java.util.UUID;

/**
 * Thrown when an order is not found by ID.
 * Used when querying order history or performing order operations.
 */
public final class OrderNotFoundException extends DomainException {

    private final UUID orderId;

    public OrderNotFoundException(String message) {
        super(com.tradingplatform.domain.enums.ErrorCode.ORD_409, message);
        this.orderId = null;
    }

    public OrderNotFoundException(UUID orderId, String message) {
        super(com.tradingplatform.domain.enums.ErrorCode.ORD_409, message);
        this.orderId = orderId;
    }
    
    public UUID getOrderId() {
        return orderId;
    }
}
