package com.tradingplatform.domain.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderHistoryEntry (
    
    @NotNull(message="orderId cannot be Null")
    String orderId, 

    @NotNull(message="accountId cannot be Null")
    int accountId, 

    @NotBlank(message="Symbol cannot be blank") 
    String symbol, 

    @NotNull(message="Side of the trade must be mentioned")
    String side, 

    @NotNull(message="Quantity must be mentioned")
    int quantity, 

    @NotNull(message="Price must be mentioned")
    BigDecimal price, 

    @NotNull(message="Status must be mentioned")
    String status,

    @NotNull(message="createdOn cannot be Null")
    LocalDateTime createdOn
){}
    

