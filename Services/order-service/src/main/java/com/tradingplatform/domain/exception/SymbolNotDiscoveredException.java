package com.tradingplatform.domain.exception;

// Symbol discovery: Fauxnance does not recognise the symbol, or has no quote for it

public final class SymbolNotDiscoveredException extends DomainException {

    private final String symbol;

    public SymbolNotDiscoveredException(String symbol, String reason) {
        super(com.tradingplatform.domain.enums.ErrorCode.INS_404, reason);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
