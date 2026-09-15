package com.tradingplatform.domain.dto;

import java.math.BigDecimal;

import com.tradingplatform.domain.enums.OrderSide;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(
    @NotNull(message="accountId must be mentioned")
    @Positive(message="accountId must be greater than zero")
    Long accountId,

    @NotBlank(message="Symbol cannot be blank")
    @Size(max = 20, message = "Symbol cannot exceed 20 characters")
    String symbol,

    @NotNull(message="Side of the trade must be mentioned")
    OrderSide side,

    @NotNull(message="Quantity must be mentioned")
    @Positive(message="Quantity must be greater than zero")
    Integer quantity,

    @NotNull(message="Price must be mentioned")
    @Positive(message="Price must be positive")
    @Digits(integer = 18, fraction = 2, message = "Price must have at most two decimal places")
    BigDecimal price,

    @NotNull(message="Idempotency is mandatory to be mentioned")
    @Size(min = 8, max = 100, message = "Idempotency key length must be between 8 and 100")
    String idempotencyKey
) {
}