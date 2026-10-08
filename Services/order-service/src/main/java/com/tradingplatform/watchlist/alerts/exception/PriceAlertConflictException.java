package com.tradingplatform.watchlist.alerts.exception;

import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.DomainException;

/** ALR-409: the account's alert cap is reached, or the same active alert already exists */
public final class PriceAlertConflictException extends DomainException {

    public PriceAlertConflictException(String message) {
        super(ErrorCode.ALR_409, message);
    }
}
