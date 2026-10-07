package com.tradingplatform.watchlist.alerts.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A price level one account wants to be told about (row in PRICE_ALERTS). Populated by MyBatis. */
public class PriceAlert {

    /** deactivated_reason when a ONCE alert has fired */
    public static final String TRIGGERED_ONCE = "TRIGGERED_ONCE";
    /** deactivated_reason when the customer turned the alert off */
    public static final String PAUSED = "PAUSED";

    private Long alertId;
    private Long accountId;
    private String symbol;
    private AlertDirection direction;
    private BigDecimal threshold;
    private AlertMode mode;
    private boolean active;
    private Instant lastQuoteAt;
    private Instant lastTriggeredAt;
    private BigDecimal lastTriggeredPrice;
    private int triggerCount;
    private String deactivatedReason;
    private Instant createdAt;
    private Instant updatedAt;

    public PriceAlert() {
    }

    public PriceAlert(Long accountId, String symbol, AlertDirection direction, BigDecimal threshold, AlertMode mode) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.direction = direction;
        this.threshold = threshold;
        this.mode = mode;
        this.active = true;
    }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public AlertDirection getDirection() { return direction; }
    public void setDirection(AlertDirection direction) { this.direction = direction; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public AlertMode getMode() { return mode; }
    public void setMode(AlertMode mode) { this.mode = mode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getLastQuoteAt() { return lastQuoteAt; }
    public void setLastQuoteAt(Instant lastQuoteAt) { this.lastQuoteAt = lastQuoteAt; }

    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(Instant lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }

    public BigDecimal getLastTriggeredPrice() { return lastTriggeredPrice; }
    public void setLastTriggeredPrice(BigDecimal lastTriggeredPrice) { this.lastTriggeredPrice = lastTriggeredPrice; }

    public int getTriggerCount() { return triggerCount; }
    public void setTriggerCount(int triggerCount) { this.triggerCount = triggerCount; }

    public String getDeactivatedReason() { return deactivatedReason; }
    public void setDeactivatedReason(String deactivatedReason) { this.deactivatedReason = deactivatedReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
