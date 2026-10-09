package com.tradingplatform.notifications.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.tradingplatform.notifications.dto.NotificationHistoryItem;
import com.tradingplatform.notifications.mapper.OrderNotificationMapper;
import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;

/**
 * An account's notification history for the Notifications page: order outcomes and price alert
 * firings on every channel (not only the in-app ones the bell polls), newest first, each with the
 * channels it went out on.
 */
@Service
public class NotificationHistoryService {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 200;

    private final OrderNotificationMapper orderNotificationMapper;
    private final PriceAlertTriggerMapper triggerMapper;

    public NotificationHistoryService(OrderNotificationMapper orderNotificationMapper, PriceAlertTriggerMapper triggerMapper) {
        this.orderNotificationMapper = Objects.requireNonNull(orderNotificationMapper);
        this.triggerMapper = Objects.requireNonNull(triggerMapper);
    }

    /**
     * @param limit how many items, clamped to 1..{@value #MAX_LIMIT} (default {@value #DEFAULT_LIMIT})
     */
    public List<NotificationHistoryItem> history(long accountId, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        List<NotificationHistoryItem> items = new ArrayList<>();
        for (OrderNotification n : orderNotificationMapper.findHistory(accountId, size)) {
            items.add(fromOrder(n));
        }
        for (PriceAlertTrigger t : triggerMapper.findHistoryByAccountId(accountId, size)) {
            items.add(fromTrigger(t));
        }
        items.sort(Comparator.comparing(NotificationHistoryItem::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(NotificationHistoryItem::id, Comparator.reverseOrder()));
        return items.size() > size ? List.copyOf(items.subList(0, size)) : items;
    }

    static NotificationHistoryItem fromOrder(OrderNotification n) {
        Instant at = n.getOccurredAt() != null ? n.getOccurredAt() : n.getCreatedAt();
        return new NotificationHistoryItem(
            "order-" + n.getNotificationId(),
            NotificationHistoryItem.Kind.ORDER,
            at,
            n.getStatus(),
            n.getSymbol(),
            channels(n.getChannels()),
            n.isInApp(),
            new NotificationHistoryItem.OrderDetails(n.getOrderId(), n.getEventType(), n.getSide(), n.getQuantity(),
                n.getLimitPrice(), n.getExecutedPrice(), n.getReason()),
            null);
    }

    static NotificationHistoryItem fromTrigger(PriceAlertTrigger t) {
        return new NotificationHistoryItem(
            "alert-" + t.getTriggerId(),
            NotificationHistoryItem.Kind.PRICE_ALERT,
            t.getTriggeredAt(),
            t.getDeliveryStatus() == null ? null : t.getDeliveryStatus().name(),
            t.getSymbol(),
            channels(t.getChannels()),
            t.isInApp(),
            null,
            new NotificationHistoryItem.PriceAlertDetails(t.getAlertId(), t.getTriggerId(),
                t.getDirection() == null ? null : t.getDirection().name(), t.getThreshold(),
                t.getMode() == null ? null : t.getMode().name(), t.getPrice(), t.getQuoteAt(), t.getReason()));
    }

    /** "EMAIL,IN_APP" -> [EMAIL, IN_APP]; nothing recorded -> [IN_APP] (the platform default) */
    static List<String> channels(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of("IN_APP");
        }
        return Arrays.stream(stored.split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList();
    }
}
