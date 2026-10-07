package com.tradingplatform.strategy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.tradingplatform.strategy.model.ExecutionOutcome;
import com.tradingplatform.strategy.model.StrategySignal;

/**
 * One trigger of a strategy: the signal, the numbers and sentence behind it, and what happened.
 *
 * @param quoteAt observation time of the quote that triggered it
 */
public record StrategyExecutionResponse(
    Long executionId,
    Long strategyId,
    String symbol,
    Instant quoteAt,
    StrategySignal signal,
    BigDecimal price,
    int quantity,
    BigDecimal notional,
    Map<String, Object> indicators,
    String reason,
    ExecutionOutcome outcome,
    String orderId,
    String errorCode,
    String errorMessage,
    Instant createdAt
) {
}
