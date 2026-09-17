package com.tradingplatform.domain.exception;
// Rule 7: A SELL is larger than the quantity held. 

public final class InsufficientHoldingsException extends DomainException {

    private final int held;
    private final int requested;

    public InsufficientHoldingsException(int held, int requested) {
        super(com.tradingplatform.domain.enums.ErrorCode.ORD_409, "Insufficient holdings");
        this.held = held;
        this.requested = requested;
    }

    public int getHeld() {
        return held;
    }

    public int getRequested() {
        return requested;
    }
}
