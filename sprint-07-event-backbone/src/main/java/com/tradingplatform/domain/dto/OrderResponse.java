package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record OrderResponse(
    @NotBlank(message = "orderId cannot be blank")
    String orderId,

    @NotNull(message = "Status cannot be Null")
    OrderStatus status,

    @NotBlank(message = "message cannot be blank")
    String message,

    @NotBlank(message = "Symbol cannot be blank")
    String symbol,

    @NotNull(message = "Side of the trade must be mentioned")
    OrderSide side,

    @NotNull(message = "Quantity must be mentioned")
    @Positive(message = "Quantity must be positive")
    Integer quantity,

    @NotNull(message = "Price must be mentioned")
    @DecimalMin(value = "0.00", inclusive = false, message = "Price must be greater than zero")
    @Digits(integer = 18, fraction = 2, message = "Price must have at most two decimal places")
    BigDecimal price,

    @NotNull(message = "Fee must be mentioned")
    @DecimalMin(value = "0.00", inclusive = true, message = "Fee must be non-negative")
    @Digits(integer = 18, fraction = 2, message = "Fee must have at most two decimal places")
    BigDecimal fee,

    // executedPrice is only populated in SYNC mode when order is FILLED/REJECTED
    // In ASYNC mode, this field is null since order will be filled asynchronously by Trade Executor
    Optional<BigDecimal> executedPrice
) {
}