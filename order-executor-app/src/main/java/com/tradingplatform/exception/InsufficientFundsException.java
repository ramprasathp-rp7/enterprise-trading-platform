package com.tradingplatform.domain.exception;

import java.math.BigDecimal;
import com.tradingplatform.enums.ErrorCode;
// Rule 6: a BUY costs more than the available cash balance.

public final class InsufficientFundsException extends DomainException {

    private final BigDecimal available;
    private final BigDecimal required;

    public InsufficientFundsException(BigDecimal available, BigDecimal required) {
        super(ErrorCode.ORD_400, "Insufficient funds");
        this.available = available;
        this.required = required;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getRequired() {
        return required;
    }
}
