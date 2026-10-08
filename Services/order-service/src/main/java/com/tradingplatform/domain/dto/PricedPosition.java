package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Priced position: every open holding with its cost basis, market value and unrealised P&L.
 * Fields match the binding contract in portfolio-api.yaml exactly.
 * lastPrice, marketValue, unrealisedPnl, unrealisedPnlPercent, priceAsOf can be null when instrument cannot be priced.
 *
 * Instrument-level fields (averageCost, costBasis, lastPrice, marketValue, unrealisedPnl) are in
 * {@code currency}, the instrument's own. The *Account fields are the same figures in the account's
 * currency: cost at the fill rates of the buys, value at {@code fxRate}, the live instrument-to-account
 * rate. Portfolio totals sum the *Account fields. They are null when no FX rate is available.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PricedPosition {
    
    @JsonProperty("accountId")
    private final Long accountId;
    
    @JsonProperty("symbol")
    private final String symbol;
    
    @JsonProperty("quantity")
    private final Integer quantity;
    
    @JsonProperty("averageCost")
    private final BigDecimal averageCost;
    
    @JsonProperty("costBasis")
    private final BigDecimal costBasis;
    
    @JsonProperty("lastPrice")
    private final BigDecimal lastPrice;
    
    @JsonProperty("marketValue")
    private final BigDecimal marketValue;
    
    @JsonProperty("unrealisedPnl")
    private final BigDecimal unrealisedPnl;
    
    @JsonProperty("unrealisedPnlPercent")
    private final BigDecimal unrealisedPnlPercent;
    
    @JsonProperty("currency")
    private final String currency;
    
    @JsonProperty("priceAsOf")
    private final Instant priceAsOf;
    
    @JsonProperty("stale")
    private final Boolean stale;

    @JsonProperty("fxRate")
    private final BigDecimal fxRate;

    @JsonProperty("averageCostAccount")
    private final BigDecimal averageCostAccount;

    @JsonProperty("costBasisAccount")
    private final BigDecimal costBasisAccount;

    @JsonProperty("marketValueAccount")
    private final BigDecimal marketValueAccount;

    @JsonProperty("unrealisedPnlAccount")
    private final BigDecimal unrealisedPnlAccount;

    public PricedPosition(Long accountId, String symbol, Integer quantity, BigDecimal averageCost,
                         BigDecimal costBasis, BigDecimal lastPrice, BigDecimal marketValue,
                         BigDecimal unrealisedPnl, BigDecimal unrealisedPnlPercent, String currency,
                         Instant priceAsOf, Boolean stale, BigDecimal fxRate, BigDecimal averageCostAccount,
                         BigDecimal costBasisAccount, BigDecimal marketValueAccount, BigDecimal unrealisedPnlAccount) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "quantity cannot be null");
        this.averageCost = Objects.requireNonNull(averageCost, "averageCost cannot be null");
        this.costBasis = Objects.requireNonNull(costBasis, "costBasis cannot be null");
        this.lastPrice = lastPrice;
        this.marketValue = marketValue;
        this.unrealisedPnl = unrealisedPnl;
        this.unrealisedPnlPercent = unrealisedPnlPercent;
        this.currency = Objects.requireNonNull(currency, "currency cannot be null");
        this.priceAsOf = priceAsOf;
        this.stale = Objects.requireNonNull(stale, "stale cannot be null");
        this.fxRate = fxRate;
        this.averageCostAccount = averageCostAccount;
        this.costBasisAccount = costBasisAccount;
        this.marketValueAccount = marketValueAccount;
        this.unrealisedPnlAccount = unrealisedPnlAccount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public BigDecimal getUnrealisedPnl() {
        return unrealisedPnl;
    }

    public BigDecimal getUnrealisedPnlPercent() {
        return unrealisedPnlPercent;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPriceAsOf() {
        return priceAsOf;
    }

    public Boolean getStale() {
        return stale;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public BigDecimal getAverageCostAccount() {
        return averageCostAccount;
    }

    public BigDecimal getCostBasisAccount() {
        return costBasisAccount;
    }

    public BigDecimal getMarketValueAccount() {
        return marketValueAccount;
    }

    public BigDecimal getUnrealisedPnlAccount() {
        return unrealisedPnlAccount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PricedPosition that = (PricedPosition) o;
        return Objects.equals(accountId, that.accountId) &&
                Objects.equals(symbol, that.symbol) &&
                Objects.equals(quantity, that.quantity) &&
                Objects.equals(averageCost, that.averageCost) &&
                Objects.equals(costBasis, that.costBasis) &&
                Objects.equals(lastPrice, that.lastPrice) &&
                Objects.equals(marketValue, that.marketValue) &&
                Objects.equals(unrealisedPnl, that.unrealisedPnl) &&
                Objects.equals(unrealisedPnlPercent, that.unrealisedPnlPercent) &&
                Objects.equals(currency, that.currency) &&
                Objects.equals(priceAsOf, that.priceAsOf) &&
                Objects.equals(stale, that.stale) &&
                Objects.equals(fxRate, that.fxRate) &&
                Objects.equals(averageCostAccount, that.averageCostAccount) &&
                Objects.equals(costBasisAccount, that.costBasisAccount) &&
                Objects.equals(marketValueAccount, that.marketValueAccount) &&
                Objects.equals(unrealisedPnlAccount, that.unrealisedPnlAccount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol, quantity, averageCost, costBasis, lastPrice,
                marketValue, unrealisedPnl, unrealisedPnlPercent, currency, priceAsOf, stale, fxRate,
                averageCostAccount, costBasisAccount, marketValueAccount, unrealisedPnlAccount);
    }

    @Override
    public String toString() {
        return "PricedPosition{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", averageCost=" + averageCost +
                ", costBasis=" + costBasis +
                ", lastPrice=" + lastPrice +
                ", marketValue=" + marketValue +
                ", unrealisedPnl=" + unrealisedPnl +
                ", unrealisedPnlPercent=" + unrealisedPnlPercent +
                ", currency='" + currency + '\'' +
                ", priceAsOf=" + priceAsOf +
                ", stale=" + stale +
                '}';
    }
}
