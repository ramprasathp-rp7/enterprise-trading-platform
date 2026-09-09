package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record BalanceResponse (
    @NotNull(message="accountId cannot be Null")
    int accountId, 

    @PositiveOrZero (message="Balance cannot be negative")
    BigDecimal cashBalance, 

    @NotNull(message="Currency cannot be Null ")
    String currency, 

    @NotNull(message="asOf cannot be Null")
    LocalDateTime asOf
){}
