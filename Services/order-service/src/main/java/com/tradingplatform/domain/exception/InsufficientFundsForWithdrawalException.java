package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;
import java.math.BigDecimal;

/**
 * Thrown when an account has insufficient funds to process a withdrawal.
 */
public class InsufficientFundsForWithdrawalException extends DomainException {

    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;

    public InsufficientFundsForWithdrawalException(BigDecimal requestedAmount, BigDecimal availableBalance) {
        super(ErrorCode.TRANS_400,
              String.format("Insufficient funds for withdrawal. Requested: %s, Available: %s",
                          requestedAmount, availableBalance));
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
