package com.tradingplatform.strategy.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A rule one account wants traded automatically (row in TRADING_STRATEGIES). Populated by MyBatis. */
public class TradingStrategy {

    /** disabled_reason when the customer turned the strategy off */
    public static final String DISABLED_BY_USER = "USER";
    /** disabled_reason when the strategy stopped after too many failed orders */
    public static final String DISABLED_BY_FAILURES = "FAILURES";

    private Long strategyId;
    private Long accountId;
    private String symbol;
    private StrategyType strategyType;
    private Integer shortWindow;
    private Integer longWindow;
    private Integer bandWindow;
    private BigDecimal bandK;
    private TradeSide tradeSide;
    private int quantity;
    private int maxPosition;
    private BigDecimal maxDailyNotional;
    private int maxConsecutiveFailures;
    private boolean active;
    private String disabledReason;
    private IndicatorState lastState;
    private Instant lastEvaluatedQuoteAt;
    private int consecutiveFailures;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public StrategyType getStrategyType() { return strategyType; }
    public void setStrategyType(StrategyType strategyType) { this.strategyType = strategyType; }

    public Integer getShortWindow() { return shortWindow; }
    public void setShortWindow(Integer shortWindow) { this.shortWindow = shortWindow; }

    public Integer getLongWindow() { return longWindow; }
    public void setLongWindow(Integer longWindow) { this.longWindow = longWindow; }

    public Integer getBandWindow() { return bandWindow; }
    public void setBandWindow(Integer bandWindow) { this.bandWindow = bandWindow; }

    public BigDecimal getBandK() { return bandK; }
    public void setBandK(BigDecimal bandK) { this.bandK = bandK; }

    public TradeSide getTradeSide() { return tradeSide; }
    public void setTradeSide(TradeSide tradeSide) { this.tradeSide = tradeSide; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getMaxPosition() { return maxPosition; }
    public void setMaxPosition(int maxPosition) { this.maxPosition = maxPosition; }

    public BigDecimal getMaxDailyNotional() { return maxDailyNotional; }
    public void setMaxDailyNotional(BigDecimal maxDailyNotional) { this.maxDailyNotional = maxDailyNotional; }

    public int getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) { this.maxConsecutiveFailures = maxConsecutiveFailures; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDisabledReason() { return disabledReason; }
    public void setDisabledReason(String disabledReason) { this.disabledReason = disabledReason; }

    public IndicatorState getLastState() { return lastState; }
    public void setLastState(IndicatorState lastState) { this.lastState = lastState; }

    public Instant getLastEvaluatedQuoteAt() { return lastEvaluatedQuoteAt; }
    public void setLastEvaluatedQuoteAt(Instant lastEvaluatedQuoteAt) { this.lastEvaluatedQuoteAt = lastEvaluatedQuoteAt; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
