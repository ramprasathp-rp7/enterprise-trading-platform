package com.tradingplatform.domain.exception;
// No quote for the symbol in the market data cache

public final class QuoteNotFoundException extends DomainException {

    private final String symbol;

    public QuoteNotFoundException(String symbol) {
        super(com.tradingplatform.domain.enums.ErrorCode.MKT_404, "No cached quote for this symbol");
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
