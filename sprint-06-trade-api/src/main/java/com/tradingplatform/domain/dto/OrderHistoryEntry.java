package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderHistoryEntry(
    @NotBlank(message = "orderId cannot be blank")
    String orderId,

    @NotNull(message = "accountId cannot be Null")
    Long accountId,

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

    @Digits(integer = 18, fraction = 2, message = "Executed price must have at most two decimal places")
    BigDecimal executedPrice,

    @NotNull(message = "Status must be mentioned")
    OrderStatus status,

    @NotBlank(message = "Idempotency key cannot be blank")
    @Size(min = 8, max = 100, message = "Idempotency key must be between 8 and 100 characters")
    String idempotencyKey,

    @NotNull(message = "createdOn cannot be Null")
    Instant createdOn
) {
}


