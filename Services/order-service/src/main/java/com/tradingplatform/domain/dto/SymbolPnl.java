package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Per-symbol breakdown of realised and unrealised profit and loss.
 * Present only when bySymbol=true in the request.
 */
public final class SymbolPnl {
    
    @JsonProperty("symbol")
    private final String symbol;
    
    @JsonProperty("realisedPnl")
    private final BigDecimal realisedPnl;
    
    @JsonProperty("unrealisedPnl")
    private final BigDecimal unrealisedPnl;
    
    @JsonProperty("totalPnl")
    private final BigDecimal totalPnl;

    public SymbolPnl(String symbol, BigDecimal realisedPnl, BigDecimal unrealisedPnl, BigDecimal totalPnl) {
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl cannot be null");
        this.unrealisedPnl = Objects.requireNonNull(unrealisedPnl, "unrealisedPnl cannot be null");
        this.totalPnl = Objects.requireNonNull(totalPnl, "totalPnl cannot be null");
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getRealisedPnl() {
        return realisedPnl;
    }

    public BigDecimal getUnrealisedPnl() {
        return unrealisedPnl;
    }

    public BigDecimal getTotalPnl() {
        return totalPnl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SymbolPnl symbolPnl = (SymbolPnl) o;
        return Objects.equals(symbol, symbolPnl.symbol) &&
                Objects.equals(realisedPnl, symbolPnl.realisedPnl) &&
                Objects.equals(unrealisedPnl, symbolPnl.unrealisedPnl) &&
                Objects.equals(totalPnl, symbolPnl.totalPnl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, realisedPnl, unrealisedPnl, totalPnl);
    }

    @Override
    public String toString() {
        return "SymbolPnl{" +
                "symbol='" + symbol + '\'' +
                ", realisedPnl=" + realisedPnl +
                ", unrealisedPnl=" + unrealisedPnl +
                ", totalPnl=" + totalPnl +
                '}';
    }
}
