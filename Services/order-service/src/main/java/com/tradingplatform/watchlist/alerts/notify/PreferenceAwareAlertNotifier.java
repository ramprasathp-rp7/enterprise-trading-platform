package com.tradingplatform.watchlist.alerts.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.tradingplatform.notifications.channel.NotificationDispatcher;
import com.tradingplatform.notifications.channel.NotificationMessage;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.ResolvedAlertChannels;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;

/**
 * Sends a fired price alert on the channels the customer chose in their alert preferences,
 * the same way order notifications are sent (see {@link NotificationDispatcher}):
 *
 * - IN_APP: the trigger appears in the UI bell (in_app = true on the trigger)
 * - EMAIL / SMS: handed to the channel senders (stubs today: logged, not delivered)
 *
 * The trigger is always marked DELIVERED through {@link InAppAlertNotifier} (it was handed to its
 * channels), and that happens before email and SMS go out: if marking it fails, the evaluator retries
 * the firing and nothing has been sent yet, so a retry never emails or texts twice.
 */
@Primary
@Component
public class PreferenceAwareAlertNotifier implements AlertNotifier {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceAwareAlertNotifier.class);

    private final InAppAlertNotifier deliveryRecorder;
    private final NotificationDispatcher dispatcher;
    private final PriceAlertTriggerMapper triggerMapper;

    public PreferenceAwareAlertNotifier(InAppAlertNotifier deliveryRecorder, NotificationDispatcher dispatcher,
                                        PriceAlertTriggerMapper triggerMapper) {
        this.deliveryRecorder = deliveryRecorder;
        this.dispatcher = dispatcher;
        this.triggerMapper = triggerMapper;
    }

    @Override
    public void notify(PriceAlertNotification notification) {
        ResolvedAlertChannels channels = dispatcher.resolveChannels(notification.accountId());
        boolean inApp = channels.channels().contains(AlertChannel.IN_APP);
        triggerMapper.updateChannels(notification.triggerId(), NotificationDispatcher.channelNames(channels), inApp);

        // Throws if the trigger is gone: the evaluator marks it FAILED and retries, nothing sent yet
        deliveryRecorder.notify(notification);

        dispatcher.send(messageFor(notification), channels);
        logger.info("Price alert sent: accountId={}, alertId={}, triggerId={}, channels={}",
            notification.accountId(), notification.alertId(), notification.triggerId(),
            NotificationDispatcher.channelNames(channels));
    }

    /** Same wording as the UI bell: "AAPL is at 190.10, above your 190.00 level. This alert is now off." */
    static NotificationMessage messageFor(PriceAlertNotification n) {
        String side = n.direction() == AlertDirection.ABOVE ? "above" : "below";
        String off = n.mode() == AlertMode.ONCE ? " This alert is now off." : "";
        String body = String.format("%s is at %s, %s your %s level.%s", n.symbol(),
            NotificationDispatcher.money(n.price()), side, NotificationDispatcher.money(n.threshold()), off);
        return new NotificationMessage(n.accountId(), "price alert " + n.alertId(), "Price alert: " + n.symbol(), body);
    }
}
