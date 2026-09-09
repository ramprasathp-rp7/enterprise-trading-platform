package com.tradingplatform.domain.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderResponse(
    @NotNull(message="orderId cannot be Null")
    String orderId, 

    @NotNull(message="Status cannot be Null")
    String status, 
    
    String message, 

    @NotBlank(message="Symbol cannot be blank")
    String symbol, 

    @NotNull(message="Side of the trade must be mentioned")
    String side, 

    @Positive(message="Quantity must be positive")
    int quantity,

    @Positive(message="Price must be greater than zero")
    double price
){}