package com.tradingplatform.watchlist.alerts.model;

/** Whether a trigger made it to notifications. */
public enum DeliveryStatus {
    /** Recorded, being handed to the notifier. */
    PENDING,
    /** The notifier accepted it; the UI shows it in the notification bell. */
    DELIVERED,
    /** The notifier raised; the alert stays active and the quote is retried on the next evaluation. */
    FAILED
}
