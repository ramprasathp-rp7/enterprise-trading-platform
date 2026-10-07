package com.tradingplatform.notifications.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradingplatform.preferences.AlertChannel;

/**
 * SMS channel STUB: logs the text it would send, nothing is delivered.
 * The phone number is masked in the log (it is personal data).
 */
@Component
public class SmsSenderStub implements ChannelSender {

    private static final Logger logger = LoggerFactory.getLogger(SmsSenderStub.class);

    @Override
    public AlertChannel channel() {
        return AlertChannel.SMS;
    }

    @Override
    public void send(String destination, NotificationMessage message) {
        logger.info("[STUB] SMS not sent (no provider yet): to={}, accountId={}, about={}, text=\"{}: {}\"",
            mask(destination), message.accountId(), message.reference(), message.subject(), message.body());
    }

    /** "+447700900123" -> "+44*******123": country code and last three digits only */
    static String mask(String phone) {
        if (phone == null) {
            return "<none>";
        }
        if (phone.length() <= 6) {
            return "***";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 3);
    }
}
