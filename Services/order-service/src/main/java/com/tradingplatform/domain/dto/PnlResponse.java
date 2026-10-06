package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Realised and unrealised profit and loss for the account, optionally broken down by instrument.
 * Fields match the binding contract in portfolio-api.yaml exactly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PnlResponse {
    
    @JsonProperty("accountId")
    private final Long accountId;
    
    @JsonProperty("baseCurrency")
    private final String baseCurrency;
    
    @JsonProperty("from")
    private final LocalDate from;
    
    @JsonProperty("to")
    private final LocalDate to;
    
    @JsonProperty("realisedPnl")
    private final BigDecimal realisedPnl;
    
    @JsonProperty("unrealisedPnl")
    private final BigDecimal unrealisedPnl;
    
    @JsonProperty("totalPnl")
    private final BigDecimal totalPnl;
    
    @JsonProperty("bySymbol")
    private final List<SymbolPnl> bySymbol;
    
    @JsonProperty("asOf")
    private final Instant asOf;

    public PnlResponse(Long accountId, String baseCurrency, LocalDate from, LocalDate to,
                      BigDecimal realisedPnl, BigDecimal unrealisedPnl, BigDecimal totalPnl,
                      List<SymbolPnl> bySymbol, Instant asOf) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency cannot be null");
        this.from = from;
        this.to = to;
        this.realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl cannot be null");
        this.unrealisedPnl = Objects.requireNonNull(unrealisedPnl, "unrealisedPnl cannot be null");
        this.totalPnl = Objects.requireNonNull(totalPnl, "totalPnl cannot be null");
        this.bySymbol = bySymbol;
        this.asOf = Objects.requireNonNull(asOf, "asOf cannot be null");
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
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

    public List<SymbolPnl> getBySymbol() {
        return bySymbol;
    }

    public Instant getAsOf() {
        return asOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PnlResponse that = (PnlResponse) o;
        return Objects.equals(accountId, that.accountId) &&
                Objects.equals(baseCurrency, that.baseCurrency) &&
                Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(realisedPnl, that.realisedPnl) &&
                Objects.equals(unrealisedPnl, that.unrealisedPnl) &&
                Objects.equals(totalPnl, that.totalPnl) &&
                Objects.equals(bySymbol, that.bySymbol) &&
                Objects.equals(asOf, that.asOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, baseCurrency, from, to, realisedPnl, unrealisedPnl,
                totalPnl, bySymbol, asOf);
    }

    @Override
    public String toString() {
        return "PnlResponse{" +
                "accountId=" + accountId +
                ", baseCurrency='" + baseCurrency + '\'' +
                ", from=" + from +
                ", to=" + to +
                ", realisedPnl=" + realisedPnl +
                ", unrealisedPnl=" + unrealisedPnl +
                ", totalPnl=" + totalPnl +
                ", bySymbol=" + bySymbol +
                ", asOf=" + asOf +
                '}';
    }
}
