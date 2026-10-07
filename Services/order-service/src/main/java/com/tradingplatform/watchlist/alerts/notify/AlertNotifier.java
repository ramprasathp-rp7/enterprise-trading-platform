package com.tradingplatform.watchlist.alerts.notify;

/**
 * Hand-off from the alert evaluator to notifications.
 *
 * The evaluator decides that an alert fired; it does not decide how the customer hears about it.
 * Implementations own the channel (in-app today; email or push could be added without touching
 * the evaluator). An implementation signals failure by throwing: the evaluator then marks the
 * trigger FAILED, keeps the alert active and retries the same quote on its next run.
 */
public interface AlertNotifier {

    void notify(PriceAlertNotification notification);
}
