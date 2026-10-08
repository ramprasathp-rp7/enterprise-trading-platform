package com.tradingplatform.notifications.service;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.notifications.channel.NotificationDispatcher;
import com.tradingplatform.notifications.channel.NotificationMessage;
import com.tradingplatform.preferences.AdminPreferenceResolver;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;

/**
 * Notifies an admin on the channels they chose in their admin preferences.
 *
 * EMAIL and SMS go through the same {@link NotificationDispatcher} and sender stubs as customer
 * notifications. IN_APP is the UI bell, which the admin's browser adds itself when the answer
 * says IN_APP is one of the channels.
 */
@Service
public class AdminNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AdminNotificationService.class);

    private final AdminPreferenceResolver preferenceResolver;
    private final NotificationDispatcher dispatcher;

    public AdminNotificationService(AdminPreferenceResolver preferenceResolver, NotificationDispatcher dispatcher) {
        this.preferenceResolver = Objects.requireNonNull(preferenceResolver, "preferenceResolver cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
    }

    /**
     * Tells the admin who registered a customer that the registration went through.
     *
     * @param adminUserId the acting admin, from the verified token
     * @param username    the new customer's username
     * @param accountId   the account the customer was registered for
     * @return the channels the notification went to, in AlertChannel order
     */
    public List<AlertChannel> customerRegistered(String adminUserId, String username, long accountId) {
        List<AlertTarget> targets = preferenceResolver.resolveAdminAlertTargets(adminUserId);
        NotificationMessage message = new NotificationMessage(
                accountId,
                "customer registration " + username,
                "Customer registered",
                String.format("Customer '%s' was registered for account %d.", username, accountId));
        dispatcher.sendTo(message, targets);

        List<AlertChannel> channels = targets.stream().map(AlertTarget::channel).toList();
        logger.info("Customer registration notice for accountId={} sent to admin on {}", accountId, channels);
        return channels;
    }
}
