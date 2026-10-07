package com.tradingplatform.strategy.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One trigger of a strategy and what came of it (row in STRATEGY_EXECUTIONS). Populated by MyBatis. */
public class StrategyExecution {

    private Long executionId;
    private Long strategyId;
    private Long accountId;
    private String symbol;
    /** Observation time of the quote that triggered the strategy */
    private Instant quoteReceivedAt;
    private StrategySignal signal;
    private BigDecimal price;
    private int quantity;
    private BigDecimal notional;
    /** Indicator values behind the trigger, as a JSON object */
    private String indicatorsJson;
    private String reason;
    private ExecutionOutcome outcome;
    private String orderId;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Instant getQuoteReceivedAt() { return quoteReceivedAt; }
    public void setQuoteReceivedAt(Instant quoteReceivedAt) { this.quoteReceivedAt = quoteReceivedAt; }

    public StrategySignal getSignal() { return signal; }
    public void setSignal(StrategySignal signal) { this.signal = signal; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getNotional() { return notional; }
    public void setNotional(BigDecimal notional) { this.notional = notional; }

    public String getIndicatorsJson() { return indicatorsJson; }
    public void setIndicatorsJson(String indicatorsJson) { this.indicatorsJson = indicatorsJson; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public ExecutionOutcome getOutcome() { return outcome; }
    public void setOutcome(ExecutionOutcome outcome) { this.outcome = outcome; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
