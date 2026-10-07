package com.tradingplatform.notifications.channel;

/**
 * What a channel sends: a short subject and a one-line body in plain language.
 *
 * @param accountId the account being notified
 * @param reference what the message is about, for logs, e.g. "order 6f1c…" or "price alert 12"
 * @param subject   e.g. "Order completed"
 * @param body      e.g. "Good news! You bought 10 AAPL at 189.30."
 */
public record NotificationMessage(long accountId, String reference, String subject, String body) {
}
