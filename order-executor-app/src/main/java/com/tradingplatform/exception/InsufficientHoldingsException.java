package com.tradingplatform.domain.exception;
// Rule 7: A SELL is larger than the quantity held. 

import com.tradingplatform.enums.ErrorCode;

public final class InsufficientHoldingsException extends DomainException {

    private final int held;
    private final int requested;

    public InsufficientHoldingsException(int held, int requested) {
        super(ErrorCode.ORD_409, "Insufficient holdings");
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
