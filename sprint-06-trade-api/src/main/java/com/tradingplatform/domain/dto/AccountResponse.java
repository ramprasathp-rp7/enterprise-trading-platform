package com.tradingplatform.domain.dto;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AccountResponse (
    @NotNull(message="Id cannot be Null")
    int id, 

    @NotNull(message="accountId cannot be Null")
    String accountId, 

    @NotNull(message="Name cannot be Null")
    String holderName, 

    @PositiveOrZero (message="Balance cannot be negative")
    BigDecimal cashBalance, 

    @NotNull(message="accountStatus cannot be Null")
    String status, 

    @NotNull(message="Version number cannot be Null")
    int version, 

    @NotNull(message="lastUpdated cannot be Null")
    String lastUpdated
){}
