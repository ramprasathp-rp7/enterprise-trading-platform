package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tradingplatform.domain.enums.AccountStatus;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AccountResponse(
    @NotNull(message = "Id cannot be Null")
    Long id,

    @NotBlank(message = "accountId cannot be blank")
    @Size(max = 32, message = "accountId must be at most 32 characters")
    String accountId,

    @NotBlank(message = "Name cannot be blank")
    String holderName,

    @NotNull(message = "Balance cannot be Null")
    @PositiveOrZero(message = "Balance cannot be negative")
    @Digits(integer = 18, fraction = 2, message = "Balance must have at most two decimal places")
    BigDecimal cashBalance,

    @NotNull(message = "accountStatus cannot be Null")
    AccountStatus status,

    @NotNull(message = "Version number cannot be Null")
    Integer version,

    @NotNull(message = "lastUpdated cannot be Null")
    Instant lastUpdated
) {
}
