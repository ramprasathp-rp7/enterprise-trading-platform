package com.tradingplatform.domain.enums;

/**
 * Transaction type enumeration representing different kinds of cash movements.
 * Used to categorize transactions in the account's transaction history.
 */
public enum TransactionType {
    DEPOSIT,    // Money added to account
    WITHDRAW,   // Money removed from account
    FEE,        // Trading fee charged
    BUY,        // Buy order settlement
    SELL        // Sell order settlement
}
