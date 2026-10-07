package com.tradingplatform.watchlist.alerts.exception;

import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.DomainException;

/** ALR-404: the price alert does not exist for this account */
public final class PriceAlertNotFoundException extends DomainException {

    private final Long alertId;

    public PriceAlertNotFoundException(Long alertId) {
        super(ErrorCode.ALR_404, "Price alert not found");
        this.alertId = alertId;
    }

    public Long getAlertId() {
        return alertId;
    }
}
