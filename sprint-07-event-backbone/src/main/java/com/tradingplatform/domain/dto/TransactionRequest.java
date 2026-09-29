package com.tradingplatform.domain.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for deposit and withdrawal transactions.
 */
public record TransactionRequest(
    @NotNull(message = "amount cannot be null")
    @Positive(message = "amount must be positive")
    @Digits(integer = 18, fraction = 2, message = "amount must have at most two decimal places")
    BigDecimal amount
) {
}
