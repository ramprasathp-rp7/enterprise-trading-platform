package com.tradingplatform.enums;

public enum OrderStatus {
    NEW,
    FILLED,
    REJECTED,
    CANCELLED; 

    public boolean isTerminal() {
        return this != NEW;
    }
}
