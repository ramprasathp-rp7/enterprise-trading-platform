package com.tradingplatform.domain.dto;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PositionResponse (
    @NotNull(message="accountId(int) must be mentioned")
    int accountId,
    
    @NotBlank (message="Symbol cannot be blank")
    String symbol, 

    @PositiveOrZero(message="Quantity cannot be negative")
    int quantity, 

    @NotNull(message="AverageCost cannot be Null")
    BigDecimal averageCost
){}