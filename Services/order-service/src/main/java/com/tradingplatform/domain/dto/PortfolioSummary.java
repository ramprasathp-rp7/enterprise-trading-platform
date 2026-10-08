package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Portfolio summary: cash, total market value, total cost basis and P&L totals for one account.
 * Fields match the binding contract in portfolio-api.yaml exactly.
 */
public final class PortfolioSummary {
    
    @JsonProperty("accountId")
    private final Long accountId;
    
    @JsonProperty("baseCurrency")
    private final String baseCurrency;
    
    @JsonProperty("cashBalance")
    private final BigDecimal cashBalance;
    
    @JsonProperty("marketValue")
    private final BigDecimal marketValue;
    
    @JsonProperty("costBasis")
    private final BigDecimal costBasis;
    
    @JsonProperty("unrealisedPnl")
    private final BigDecimal unrealisedPnl;
    
    @JsonProperty("unrealisedPnlPercent")
    private final BigDecimal unrealisedPnlPercent;
    
    @JsonProperty("realisedPnl")
    private final BigDecimal realisedPnl;
    
    @JsonProperty("totalValue")
    private final BigDecimal totalValue;
    
    @JsonProperty("positionCount")
    private final Integer positionCount;
    
    @JsonProperty("partial")
    private final Boolean partial;
    
    @JsonProperty("asOf")
    private final Instant asOf;

    public PortfolioSummary(Long accountId, String baseCurrency, BigDecimal cashBalance,
                           BigDecimal marketValue, BigDecimal costBasis, BigDecimal unrealisedPnl,
                           BigDecimal unrealisedPnlPercent, BigDecimal realisedPnl,
                           BigDecimal totalValue, Integer positionCount, Boolean partial, Instant asOf) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency cannot be null");
        this.cashBalance = Objects.requireNonNull(cashBalance, "cashBalance cannot be null");
        this.marketValue = Objects.requireNonNull(marketValue, "marketValue cannot be null");
        this.costBasis = Objects.requireNonNull(costBasis, "costBasis cannot be null");
        this.unrealisedPnl = Objects.requireNonNull(unrealisedPnl, "unrealisedPnl cannot be null");
        this.unrealisedPnlPercent = unrealisedPnlPercent;
        this.realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl cannot be null");
        this.totalValue = Objects.requireNonNull(totalValue, "totalValue cannot be null");
        this.positionCount = Objects.requireNonNull(positionCount, "positionCount cannot be null");
        this.partial = Objects.requireNonNull(partial, "partial cannot be null");
        this.asOf = Objects.requireNonNull(asOf, "asOf cannot be null");
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }

    public BigDecimal getUnrealisedPnl() {
        return unrealisedPnl;
    }

    public BigDecimal getUnrealisedPnlPercent() {
        return unrealisedPnlPercent;
    }

    public BigDecimal getRealisedPnl() {
        return realisedPnl;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public Integer getPositionCount() {
        return positionCount;
    }

    public Boolean getPartial() {
        return partial;
    }

    public Instant getAsOf() {
        return asOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortfolioSummary that = (PortfolioSummary) o;
        return Objects.equals(accountId, that.accountId) &&
                Objects.equals(baseCurrency, that.baseCurrency) &&
                Objects.equals(cashBalance, that.cashBalance) &&
                Objects.equals(marketValue, that.marketValue) &&
                Objects.equals(costBasis, that.costBasis) &&
                Objects.equals(unrealisedPnl, that.unrealisedPnl) &&
                Objects.equals(unrealisedPnlPercent, that.unrealisedPnlPercent) &&
                Objects.equals(realisedPnl, that.realisedPnl) &&
                Objects.equals(totalValue, that.totalValue) &&
                Objects.equals(positionCount, that.positionCount) &&
                Objects.equals(partial, that.partial) &&
                Objects.equals(asOf, that.asOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, baseCurrency, cashBalance, marketValue, costBasis,
                unrealisedPnl, unrealisedPnlPercent, realisedPnl, totalValue, positionCount, partial, asOf);
    }

    @Override
    public String toString() {
        return "PortfolioSummary{" +
                "accountId=" + accountId +
                ", baseCurrency='" + baseCurrency + '\'' +
                ", cashBalance=" + cashBalance +
                ", marketValue=" + marketValue +
                ", costBasis=" + costBasis +
                ", unrealisedPnl=" + unrealisedPnl +
                ", unrealisedPnlPercent=" + unrealisedPnlPercent +
                ", realisedPnl=" + realisedPnl +
                ", totalValue=" + totalValue +
                ", positionCount=" + positionCount +
                ", partial=" + partial +
                ", asOf=" + asOf +
                '}';
    }
}
