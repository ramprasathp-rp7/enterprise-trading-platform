package com.tradingplatform.watchlist.alerts.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One firing of a price alert and why (row in PRICE_ALERT_TRIGGERS). Populated by MyBatis.
 * symbol, direction, threshold and mode are read from the parent alert by the mapper's join.
 */
public class PriceAlertTrigger {

    private Long triggerId;
    private Long alertId;
    private Long accountId;
    private BigDecimal price;
    private Instant quoteAt;
    private Instant triggeredAt;
    private String reason;
    private DeliveryStatus deliveryStatus;
    /** Channels the firing went out on, e.g. "EMAIL,IN_APP" (history reads only) */
    private String channels;
    /** Whether the bell shows it (history reads only) */
    private boolean inApp = true;

    private String symbol;
    private AlertDirection direction;
    private BigDecimal threshold;
    private AlertMode mode;

    public PriceAlertTrigger() {
    }

    /** A new trigger for the given alert, crossed by a quote at the given price */
    public PriceAlertTrigger(PriceAlert alert, BigDecimal price, Instant quoteAt, String reason) {
        this.alertId = alert.getAlertId();
        this.accountId = alert.getAccountId();
        this.price = price;
        this.quoteAt = quoteAt;
        this.reason = reason;
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.symbol = alert.getSymbol();
        this.direction = alert.getDirection();
        this.threshold = alert.getThreshold();
        this.mode = alert.getMode();
    }

    public Long getTriggerId() { return triggerId; }
    public void setTriggerId(Long triggerId) { this.triggerId = triggerId; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Instant getQuoteAt() { return quoteAt; }
    public void setQuoteAt(Instant quoteAt) { this.quoteAt = quoteAt; }

    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public AlertDirection getDirection() { return direction; }
    public void setDirection(AlertDirection direction) { this.direction = direction; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public AlertMode getMode() { return mode; }
    public void setMode(AlertMode mode) { this.mode = mode; }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }

    public boolean isInApp() {
        return inApp;
    }

    public void setInApp(boolean inApp) {
        this.inApp = inApp;
    }
}
