package com.tradingplatform.watchlist.alerts.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;

/** A price alert. Fields that are not set yet (never fired, still active) are left out. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PriceAlertResponse(
    Long alertId,
    Long accountId,
    String symbol,
    AlertDirection direction,
    BigDecimal threshold,
    AlertMode mode,
    boolean active,
    int triggerCount,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant lastTriggeredAt,
    BigDecimal lastTriggeredPrice,
    String deactivatedReason,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant updatedAt
) {
    public static PriceAlertResponse from(PriceAlert alert) {
        return new PriceAlertResponse(
            alert.getAlertId(), alert.getAccountId(), alert.getSymbol(), alert.getDirection(),
            alert.getThreshold(), alert.getMode(), alert.isActive(), alert.getTriggerCount(),
            alert.getLastTriggeredAt(), alert.getLastTriggeredPrice(), alert.getDeactivatedReason(),
            alert.getCreatedAt(), alert.getUpdatedAt());
    }
}
