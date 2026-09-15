package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BalanceResponse(
    @NotNull(message = "accountId cannot be Null")
    Long accountId,

    @NotNull(message = "Balance cannot be Null")
    @PositiveOrZero(message = "Balance cannot be negative")
    @Digits(integer = 18, fraction = 2, message = "Balance must have at most two decimal places")
    BigDecimal cashBalance,

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    String currency,

    @NotNull(message = "asOf cannot be Null")
    Instant asOf
) {
}
