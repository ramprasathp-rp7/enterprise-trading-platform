package com.tradingplatform.notifications.model;

import java.math.BigDecimal;

/**
 * The parts of an order a notification needs that trade events do not carry.
 * Mutable for MyBatis result mapping.
 */
public class OrderDetails {

    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal limitPrice;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
}
