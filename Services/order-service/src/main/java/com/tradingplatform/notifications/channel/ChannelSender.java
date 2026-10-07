package com.tradingplatform.notifications.channel;

import com.tradingplatform.preferences.AlertChannel;

/**
 * Sends a notification over one external channel (email or SMS).
 *
 * In-app is not a sender: it is the UI bell reading the stored notifications.
 * Today's implementations are stubs that log instead of delivering; a real provider
 * (SMTP, an SMS gateway) can replace them without touching the dispatcher.
 */
public interface ChannelSender {

    /** The channel this sender handles (EMAIL or SMS) */
    AlertChannel channel();

    /**
     * Sends the message.
     *
     * @param destination the decrypted email address or E.164 phone number from the customer's preferences
     * @param message     what to send
     */
    void send(String destination, NotificationMessage message);
}
