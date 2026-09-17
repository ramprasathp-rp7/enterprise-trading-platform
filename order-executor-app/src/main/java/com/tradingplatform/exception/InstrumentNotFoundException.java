package com.tradingplatform.domain.exception;
// Rule 3 : Instrument not found or Untradable instrument

import com.tradingplatform.enums.ErrorCode;

public final class InstrumentNotFoundException extends DomainException {

    private final String symbol;

    public InstrumentNotFoundException(String symbol) {
        super(ErrorCode.INS_404, "Instrument not found");
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
