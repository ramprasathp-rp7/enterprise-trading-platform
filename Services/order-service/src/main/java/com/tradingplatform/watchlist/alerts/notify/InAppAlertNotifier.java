package com.tradingplatform.watchlist.alerts.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;

/**
 * In-app channel: the UI's notification bell polls
 * GET /api/v1/accounts/{accountId}/price-alerts/triggers and shows DELIVERED triggers,
 * so delivering means publishing the trigger to that feed.
 */
@Component
public class InAppAlertNotifier implements AlertNotifier {

    private static final Logger logger = LoggerFactory.getLogger(InAppAlertNotifier.class);

    private final PriceAlertTriggerMapper triggerMapper;

    public InAppAlertNotifier(PriceAlertTriggerMapper triggerMapper) {
        this.triggerMapper = triggerMapper;
    }

    @Override
    public void notify(PriceAlertNotification notification) {
        if (triggerMapper.updateDeliveryStatus(notification.triggerId(), DeliveryStatus.DELIVERED) == 0) {
            throw new IllegalStateException("Price alert trigger " + notification.triggerId() + " no longer exists");
        }
        logger.info("Price alert delivered in-app: accountId={}, alertId={}, triggerId={}",
            notification.accountId(), notification.alertId(), notification.triggerId());
    }
}
