package com.tradingplatform.domain;

import java.math.BigDecimal;

public class Order {
    private final String orderId;
    private final String symbol;
    private final String side; // BUY/SELL
    private final int quantity;
    private final BigDecimal limitPrice;

    public Order(String orderId, String symbol, String side, int quantity, BigDecimal limitPrice) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
    }

    public String getOrderId() { return orderId; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
}
