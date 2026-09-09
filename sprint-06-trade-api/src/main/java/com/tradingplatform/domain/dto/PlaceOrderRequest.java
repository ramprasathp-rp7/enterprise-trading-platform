package com.tradingplatform.domain.dto;

import java.math.BigDecimal;

import com.tradingplatform.domain.enums.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(
    @NotNull(message = "accountId must be mentioned")
    @Min(value = 1, message = "accountId must be at least 1")
    Long accountId,

    @NotBlank(message = "Symbol cannot be blank")
    @Size(max = 20, message = "Symbol must be at most 20 characters")
    String symbol,

    @NotNull(message = "Side of the trade must be mentioned")
    OrderSide side,

    @NotNull(message = "Quantity must be mentioned")
    @Positive(message = "Quantity must be greater than zero")
    Integer quantity,

    @NotNull(message = "Price must be mentioned")
    @DecimalMin(value = "0.00", inclusive = false, message = "Price must be greater than zero")
    @Digits(integer = 18, fraction = 2, message = "Price must have at most two decimal places")
    BigDecimal price,

    @NotBlank(message = "Idempotency key is mandatory")
    @Size(min = 8, max = 100, message = "Idempotency key must be between 8 and 100 characters")
    String idempotencyKey
) {
}