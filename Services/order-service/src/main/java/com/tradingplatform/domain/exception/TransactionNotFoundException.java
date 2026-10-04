package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

/**
 * Thrown when a requested transaction is not found in the database.
 */
public class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(String transactionId) {
        super(ErrorCode.TRANS_404,
              String.format("Transaction '%s' not found", transactionId));
    }
}
