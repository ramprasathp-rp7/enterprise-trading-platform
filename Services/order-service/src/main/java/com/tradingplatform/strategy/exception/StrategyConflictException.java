package com.tradingplatform.strategy.exception;

import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.DomainException;

/** STR-409: the account's active strategy cap is reached, or the same active rule already exists */
public final class StrategyConflictException extends DomainException {

    public StrategyConflictException(String message) {
        super(ErrorCode.STR_409, message);
    }
}
