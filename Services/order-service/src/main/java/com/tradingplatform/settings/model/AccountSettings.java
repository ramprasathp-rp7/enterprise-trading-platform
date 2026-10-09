package com.tradingplatform.settings.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of account_settings. A plain bean so MyBatis maps it by property name.
 */
public class AccountSettings {

    /** Markets the instrument lists and searches show by default */
    public static final String MARKET_ALL = "ALL";

    private Long accountId;
    /** Most an order should cost, in the account's currency; null = no budget */
    private BigDecimal orderBudget;
    /** USA, NSE or ALL */
    private String market = MARKET_ALL;
    /** Prefilled order quantity; null = no prefill */
    private Integer defaultQuantity;
    private Instant updatedAt;

    public AccountSettings() {
    }

    public AccountSettings(Long accountId, BigDecimal orderBudget, String market, Integer defaultQuantity, Instant updatedAt) {
        this.accountId = accountId;
        this.orderBudget = orderBudget;
        this.market = market;
        this.defaultQuantity = defaultQuantity;
        this.updatedAt = updatedAt;
    }

    /** The settings of an account with nothing stored */
    public static AccountSettings defaults(long accountId) {
        return new AccountSettings(accountId, null, MARKET_ALL, null, null);
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getOrderBudget() {
        return orderBudget;
    }

    public void setOrderBudget(BigDecimal orderBudget) {
        this.orderBudget = orderBudget;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public Integer getDefaultQuantity() {
        return defaultQuantity;
    }

    public void setDefaultQuantity(Integer defaultQuantity) {
        this.defaultQuantity = defaultQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
