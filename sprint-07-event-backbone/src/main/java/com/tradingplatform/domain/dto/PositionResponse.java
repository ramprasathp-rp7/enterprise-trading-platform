package com.tradingplatform.domain.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PositionResponse(
    @NotNull(message = "accountId(int) must be mentioned")
    Long accountId,

    @NotBlank(message = "Symbol cannot be blank")
    String symbol,

    @NotNull(message = "Quantity must be mentioned")
    @PositiveOrZero(message = "Quantity cannot be negative")
    Integer quantity,

    @NotNull(message = "AverageCost cannot be Null")
    @PositiveOrZero(message = "AverageCost cannot be negative")
    @Digits(integer = 18, fraction = 2, message = "AverageCost must have at most two decimal places")
    BigDecimal averageCost
) {
}