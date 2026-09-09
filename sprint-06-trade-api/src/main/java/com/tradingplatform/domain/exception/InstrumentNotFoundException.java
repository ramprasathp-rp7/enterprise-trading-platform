package com.tradingplatform.domain.exception;
// Rule 3 : Instrument not found or Untradable instrument

public final class InstrumentNotFoundException extends DomainException {

    private final String symbol;

    public InstrumentNotFoundException(String symbol) {
        super(com.tradingplatform.domain.enums.ErrorCode.INS_404, "Instrument not found");
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
