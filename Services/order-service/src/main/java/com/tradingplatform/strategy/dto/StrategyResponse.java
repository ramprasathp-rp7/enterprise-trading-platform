package com.tradingplatform.strategy.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradeSide;
import com.tradingplatform.strategy.model.TradingStrategy;

/** A strategy as the API returns it, with its rule in words */
public record StrategyResponse(
    Long strategyId,
    String symbol,
    StrategyType strategyType,
    Integer shortWindow,
    Integer longWindow,
    Integer bandWindow,
    BigDecimal bandK,
    TradeSide tradeSide,
    int quantity,
    int maxPosition,
    BigDecimal maxDailyNotional,
    int maxConsecutiveFailures,
    boolean active,
    String disabledReason,
    IndicatorState lastState,
    Instant lastEvaluatedQuoteAt,
    int consecutiveFailures,
    String description,
    Instant createdAt,
    Instant updatedAt
) {

    public static StrategyResponse from(TradingStrategy s) {
        return new StrategyResponse(s.getStrategyId(), s.getSymbol(), s.getStrategyType(), s.getShortWindow(),
            s.getLongWindow(), s.getBandWindow(), s.getBandK(), s.getTradeSide(), s.getQuantity(), s.getMaxPosition(),
            s.getMaxDailyNotional(), s.getMaxConsecutiveFailures(), s.isActive(), s.getDisabledReason(), s.getLastState(),
            s.getLastEvaluatedQuoteAt(), s.getConsecutiveFailures(), describe(s), s.getCreatedAt(), s.getUpdatedAt());
    }

    /** e.g. "Buy 10 AAPL when the 5-point average crosses above the 20-point average; sell 10 when it crosses below." */
    static String describe(TradingStrategy s) {
        int q = s.getQuantity();
        String symbol = s.getSymbol();
        boolean ma = s.getStrategyType() == StrategyType.MA_CROSSOVER;
        String buy = ma
            ? "the " + s.getShortWindow() + "-point average crosses above the " + s.getLongWindow() + "-point average"
            : "the price falls below the " + s.getBandWindow() + "-point lower Bollinger band (" + s.getBandK().stripTrailingZeros().toPlainString() + " std dev)";
        String sell = ma
            ? "the " + s.getShortWindow() + "-point average crosses below the " + s.getLongWindow() + "-point average"
            : "the price rises above the " + s.getBandWindow() + "-point upper Bollinger band (" + s.getBandK().stripTrailingZeros().toPlainString() + " std dev)";
        return switch (s.getTradeSide()) {
            case BUY_ONLY -> "Buy " + q + " " + symbol + " when " + buy + ".";
            case SELL_ONLY -> "Sell " + q + " " + symbol + " when " + sell + ".";
            case BOTH -> "Buy " + q + " " + symbol + " when " + buy + "; sell " + q + " when " + sell + ".";
        };
    }
}
