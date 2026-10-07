package com.tradingplatform.strategy.dto;

import java.math.BigDecimal;

import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradeSide;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A new strategy. Window settings that are left out take the usual defaults:
 * MA_CROSSOVER 5 / 20 points, BOLLINGER 20 points at 2 standard deviations.
 */
public record CreateStrategyRequest(
    @NotBlank(message = "symbol is required")
    @Size(max = 20, message = "symbol cannot exceed 20 characters")
    String symbol,

    @NotNull(message = "strategyType is required")
    StrategyType strategyType,

    @Min(value = 2, message = "shortWindow must be at least 2")
    @Max(value = 99, message = "shortWindow must be at most 99")
    Integer shortWindow,

    @Min(value = 3, message = "longWindow must be at least 3")
    @Max(value = 100, message = "longWindow must be at most 100")
    Integer longWindow,

    @Min(value = 5, message = "bandWindow must be at least 5")
    @Max(value = 100, message = "bandWindow must be at most 100")
    Integer bandWindow,

    @DecimalMin(value = "0.5", message = "bandK must be at least 0.5")
    @DecimalMax(value = "5", message = "bandK must be at most 5")
    @Digits(integer = 1, fraction = 2, message = "bandK has at most 2 decimals")
    BigDecimal bandK,

    @NotNull(message = "tradeSide is required")
    TradeSide tradeSide,

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    @Max(value = 1_000_000, message = "quantity must be at most 1,000,000")
    Integer quantity,

    @NotNull(message = "maxPosition is required")
    @Min(value = 0, message = "maxPosition cannot be negative")
    @Max(value = 10_000_000, message = "maxPosition must be at most 10,000,000")
    Integer maxPosition,

    @NotNull(message = "maxDailyNotional is required")
    @Positive(message = "maxDailyNotional must be greater than 0")
    @Digits(integer = 16, fraction = 2, message = "maxDailyNotional has at most 2 decimals")
    BigDecimal maxDailyNotional,

    @Min(value = 1, message = "maxConsecutiveFailures must be at least 1")
    @Max(value = 20, message = "maxConsecutiveFailures must be at most 20")
    Integer maxConsecutiveFailures
) {
}
