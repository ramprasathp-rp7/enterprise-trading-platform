package com.tradingplatform.notifications.channel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.PreferenceResolver;
import com.tradingplatform.preferences.PreferencesUnavailableException;
import com.tradingplatform.preferences.ResolvedAlertChannels;

/**
 * Routes a customer notification (order outcomes, price alerts) to the channels the customer chose
 * in their preferences.
 *
 * Channels come from {@link PreferenceResolver} (the preferences module's published interface; see
 * decision 0003). If preferences cannot be read at all, the platform default (in-app) is used so the
 * customer still hears about the order. The account id passed to the resolver comes from the
 * trade-events message, never from a customer request.
 *
 * - IN_APP: the notification is shown in the UI bell (the stored row is marked in_app)
 * - EMAIL / SMS: handed to the {@link ChannelSender} for that channel (stubs today: logged, not delivered)
 */
@Component
public class NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final PreferenceResolver preferenceResolver;
    private final Map<AlertChannel, ChannelSender> senders = new EnumMap<>(AlertChannel.class);

    public NotificationDispatcher(PreferenceResolver preferenceResolver, List<ChannelSender> senders) {
        this.preferenceResolver = preferenceResolver;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    }

    /** The account's channels, or the platform default (in-app) when preferences are unavailable. */
    public ResolvedAlertChannels resolveChannels(long accountId) {
        try {
            return preferenceResolver.resolveAlertChannels(accountId);
        } catch (PreferencesUnavailableException e) {
            logger.warn("Preferences unavailable for accountId={}; notifying in-app only", accountId);
            return ResolvedAlertChannels.platformDefault(accountId);
        }
    }

    /** Fills in which channels the notification goes to, so the stored row records it. */
    public void applyChannels(OrderNotification notification, ResolvedAlertChannels channels) {
        notification.setInApp(channels.channels().contains(AlertChannel.IN_APP));
        notification.setChannels(channelNames(channels));
    }

    /** "EMAIL,IN_APP" */
    public static String channelNames(ResolvedAlertChannels channels) {
        return channels.channels().stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /**
     * Sends the order notification on every external channel (EMAIL, SMS) the customer chose.
     * A failing channel is logged and does not stop the others.
     */
    public void sendExternal(OrderNotification notification, ResolvedAlertChannels channels) {
        send(messageFor(notification), channels);
    }

    /**
     * Sends a message on every external channel (EMAIL, SMS) in the resolved channels; IN_APP is
     * skipped (the bell reads it from the database). A failing channel is logged and does not stop
     * the others.
     */
    public void send(NotificationMessage message, ResolvedAlertChannels channels) {
        sendTo(message, channels.targets());
    }

    /**
     * Sends a message to each external target (EMAIL, SMS); IN_APP targets are skipped. Used for
     * admin notifications, whose targets come from the AdminPreferenceResolver. A failing channel
     * is logged and does not stop the others.
     */
    public void sendTo(NotificationMessage message, List<AlertTarget> targets) {
        for (AlertTarget target : targets) {
            if (target.channel() == AlertChannel.IN_APP) {
                continue;
            }
            ChannelSender sender = senders.get(target.channel());
            if (sender == null) {
                logger.warn("No sender for channel {}; accountId={} not notified on it",
                    target.channel(), message.accountId());
                continue;
            }
            try {
                sender.send(target.destination(), message);
            } catch (RuntimeException e) {
                logger.error("{} notification failed for accountId={}, about={}",
                    target.channel(), message.accountId(), message.reference(), e);
            }
        }
    }

    /** Plain-language subject and body, worded like the UI bell */
    static NotificationMessage messageFor(OrderNotification n) {
        boolean sell = "SELL".equalsIgnoreCase(n.getSide());
        boolean details = n.getSymbol() != null && n.getQuantity() != null && n.getLimitPrice() != null;
        String order = details
            ? String.format("%s %s %s at %s", sell ? "sell" : "buy", units(n.getQuantity()), n.getSymbol(), money(n.getLimitPrice()))
            : null;

        String subject;
        String body;
        switch (n.getStatus()) {
            case "FILLED" -> {
                subject = "Order completed";
                BigDecimal price = n.getExecutedPrice() != null ? n.getExecutedPrice() : n.getLimitPrice();
                String at = price != null ? " at " + money(price) : "";
                body = n.getSymbol() != null && n.getQuantity() != null
                    ? String.format("Good news! You %s %s %s%s.", sell ? "sold" : "bought", units(n.getQuantity()), n.getSymbol(), at)
                    : "Good news! Your order was filled" + at + ".";
            }
            case "REJECTED" -> {
                subject = "Order couldn't be completed";
                String reason = n.getReason() != null && !n.getReason().isBlank()
                    ? " Reason: " + n.getReason().trim().replaceAll("\\.$", "") + "."
                    : "";
                body = (order != null ? "Your order to " + order : "Your order") + " was rejected." + reason;
            }
            case "CANCELLED" -> {
                subject = "Order cancelled";
                body = (order != null ? "Your order to " + order : "Your order") + " has been cancelled.";
            }
            default -> {
                subject = "Order update";
                body = "Your order is now " + n.getStatus().toLowerCase(Locale.ROOT) + ".";
            }
        }
        return new NotificationMessage(n.getAccountId(), "order " + n.getOrderId(), subject, body);
    }

    private static String units(int quantity) {
        return NumberFormat.getIntegerInstance(Locale.US).format(quantity);
    }

    /** 1234.5 -> "1,234.50" (up to 4 decimals) */
    public static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(4);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}
