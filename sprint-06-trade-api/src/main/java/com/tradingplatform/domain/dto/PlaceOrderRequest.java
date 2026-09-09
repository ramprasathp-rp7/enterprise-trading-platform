package com.tradingplatform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


import java.math.BigDecimal;

public record PlaceOrderRequest(
    @NotNull(message="accountId must be mentioned")
    int accountId,

    @NotNull(message="Side of the trade must be mentioned")
    String side,

    @NotBlank(message="Symbol cannot be blank")
    String symbol,

    @Positive(message="Quantity must be greater than zero")
    int quantity,

    @Positive(message="Price must be positive")
    BigDecimal price,

    @NotNull(message="Idempotency is mandatory to be mentioned")
    String idempotencyKey
){
}