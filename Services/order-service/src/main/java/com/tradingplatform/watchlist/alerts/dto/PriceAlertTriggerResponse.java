package com.tradingplatform.watchlist.alerts.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;

/** One firing of a price alert and why. */
public record PriceAlertTriggerResponse(
    Long triggerId,
    Long alertId,
    String symbol,
    AlertDirection direction,
    BigDecimal threshold,
    AlertMode mode,
    BigDecimal price,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant quoteAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant triggeredAt,
    String reason,
    DeliveryStatus deliveryStatus
) {
    public static PriceAlertTriggerResponse from(PriceAlertTrigger trigger) {
        return new PriceAlertTriggerResponse(
            trigger.getTriggerId(), trigger.getAlertId(), trigger.getSymbol(), trigger.getDirection(),
            trigger.getThreshold(), trigger.getMode(), trigger.getPrice(), trigger.getQuoteAt(),
            trigger.getTriggeredAt(), trigger.getReason(), trigger.getDeliveryStatus());
    }
}
