package com.tradingplatform.domain.dto;

import com.tradingplatform.domain.enums.OrderSide;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;


public final class PlaceOrderRequest {

    @NotNull
    @Min(1)
    private final Long accountId;

    @NotBlank
    @Size(max = 20)
    private final String symbol;

    @NotNull
    private final OrderSide side;

    
    @NotNull
    @Positive
    private final Integer quantity;

    
    @NotNull
    @Positive
    @Digits(integer = 16, fraction = 2)
    private final BigDecimal price;

    @NotBlank
    @Size(min = 8, max = 100)
    private final String idempotencyKey;

    public PlaceOrderRequest(Long accountId, String symbol, OrderSide side, Integer quantity,
                              BigDecimal price, String idempotencyKey) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
