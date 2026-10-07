package com.tradingplatform.notifications.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order outcome consumed from the trade-events topic (row of ORDER_NOTIFICATIONS).
 * Symbol, side, quantity and limit price come from the order itself, since trade events
 * carry only the order id, account, status, prices and reason.
 */
public class OrderNotification {

    private Long notificationId;
    private String eventId;
    private Long accountId;
    private String orderId;
    private String eventType;
    private String status;
    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal limitPrice;
    private BigDecimal executedPrice;
    private String reason;
    private Instant occurredAt;
    /** Channels used for this event, from the customer's preferences, e.g. "EMAIL,IN_APP" */
    private String channels = "IN_APP";
    /** True when IN_APP was one of the channels: only then does the UI bell show it */
    private boolean inApp = true;
    private Instant createdAt;

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }

    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getChannels() { return channels; }
    public void setChannels(String channels) { this.channels = channels; }

    public boolean isInApp() { return inApp; }
    public void setInApp(boolean inApp) { this.inApp = inApp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
