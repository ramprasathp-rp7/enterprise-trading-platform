package com.tradingplatform.watchlist.alerts.notify;

import java.math.BigDecimal;
import java.time.Instant;

import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;

/** What a notifier needs to tell an account that one of its alerts fired. */
public record PriceAlertNotification(
    Long triggerId,
    Long alertId,
    Long accountId,
    String symbol,
    AlertDirection direction,
    BigDecimal threshold,
    AlertMode mode,
    BigDecimal price,
    Instant quoteAt,
    String reason
) {
    public static PriceAlertNotification from(PriceAlertTrigger trigger) {
        return new PriceAlertNotification(
            trigger.getTriggerId(), trigger.getAlertId(), trigger.getAccountId(), trigger.getSymbol(),
            trigger.getDirection(), trigger.getThreshold(), trigger.getMode(), trigger.getPrice(),
            trigger.getQuoteAt(), trigger.getReason());
    }
}
