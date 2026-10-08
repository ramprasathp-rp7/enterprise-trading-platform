package com.tradingplatform.watchlist.alerts.model;

import java.math.BigDecimal;

/**
 * Which side of the threshold fires the alert. Touching the level counts as crossing it:
 * ABOVE fires when price >= threshold, BELOW fires when price <= threshold.
 */
public enum AlertDirection {
    ABOVE(">="),
    BELOW("<=");

    private final String operator;

    AlertDirection(String operator) {
        this.operator = operator;
    }

    /** The comparison written into a trigger's reason, e.g. ">=" */
    public String operator() {
        return operator;
    }

    public boolean isCrossedBy(BigDecimal price, BigDecimal threshold) {
        int cmp = price.compareTo(threshold);
        return this == ABOVE ? cmp >= 0 : cmp <= 0;
    }
}
