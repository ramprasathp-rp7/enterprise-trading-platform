package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Response DTO for account history snapshots.
 */
public record AccountHistoryResponse(
    @NotNull(message = "AccountId cannot be null")
    Long accountId,

    @NotNull(message = "SnapshotDate cannot be null")
    Instant snapshotDate,

    @NotNull(message = "CashBalance cannot be null")
    @Digits(integer = 18, fraction = 2, message = "CashBalance must have at most 2 decimal places")
    BigDecimal cashBalance,

    @NotNull(message = "Networth cannot be null")
    @Digits(integer = 18, fraction = 2, message = "Networth must have at most 2 decimal places")
    BigDecimal networth
) {
}