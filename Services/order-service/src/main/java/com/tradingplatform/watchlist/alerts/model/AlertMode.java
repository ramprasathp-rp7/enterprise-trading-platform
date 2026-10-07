package com.tradingplatform.watchlist.alerts.model;

/** What happens after an alert fires. */
public enum AlertMode {
    /** Fire on the first quote past the level, then deactivate (deactivated_reason TRIGGERED_ONCE). */
    ONCE,
    /** Fire on every new quote past the level; the alert stays active. */
    EVERY_QUOTE
}
