package com.tradingplatform.strategy.exception;

import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.DomainException;

/** STR-404: the strategy does not exist for this account */
public final class StrategyNotFoundException extends DomainException {

    public StrategyNotFoundException(Long strategyId) {
        super(ErrorCode.STR_404, "Strategy " + strategyId + " not found");
    }
}
