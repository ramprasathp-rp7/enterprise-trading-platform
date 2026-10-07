package com.tradingplatform.notifications.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tradingplatform.notifications.mapper.OrderNotificationMapper;
import com.tradingplatform.notifications.model.OrderNotification;

/**
 * Reads the order notifications built from the trade-events topic.
 */
@Service
public class OrderNotificationService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private final OrderNotificationMapper notificationMapper;

    public OrderNotificationService(OrderNotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * Notifications for an account, oldest first.
     *
     * @param accountId the account
     * @param afterId   only notifications with a larger id (the caller's cursor); null for the latest ones
     * @param limit     how many to return, clamped to 1..{@value #MAX_LIMIT} (default {@value #DEFAULT_LIMIT})
     */
    public List<OrderNotification> list(long accountId, Long afterId, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        if (afterId != null) {
            return notificationMapper.findAfter(accountId, Math.max(0, afterId), size);
        }
        List<OrderNotification> newestFirst = new ArrayList<>(notificationMapper.findLatest(accountId, size));
        Collections.reverse(newestFirst);
        return newestFirst;
    }
}
