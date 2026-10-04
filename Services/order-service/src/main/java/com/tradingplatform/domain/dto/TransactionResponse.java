package com.tradingplatform.domain.dto;

import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for individual transaction details.
 */
public record TransactionResponse(
    @NotNull(message = "transactionId cannot be null")
    UUID transactionId,

    @NotNull(message = "accountId cannot be null")
    Long accountId,

    UUID orderId,

    @NotNull(message = "transactionType cannot be null")
    TransactionType transactionType,

    @NotNull(message = "actionType cannot be null")
    TransactionAction actionType,

    @NotNull(message = "amount cannot be null")
    @PositiveOrZero(message = "amount cannot be negative")
    @Digits(integer = 18, fraction = 2, message = "amount must have at most two decimal places")
    BigDecimal amount,

    @NotNull(message = "createdOn cannot be null")
    Instant createdOn
) {
}
