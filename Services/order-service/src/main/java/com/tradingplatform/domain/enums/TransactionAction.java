package com.tradingplatform.domain.enums;

/**
 * Transaction action type enumeration representing debit or credit movement.
 * Indicates whether amount is deducted (DEBIT) or added (CREDIT) to the account.
 */
public enum TransactionAction {
    DEBIT,      // Amount deducted from account
    CREDIT      // Amount added to account
}
