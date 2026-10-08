package com.tradingplatform.notifications.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradingplatform.preferences.AlertChannel;

/**
 * Email channel STUB: logs the email it would send, nothing is delivered.
 * The address is masked in the log (it is personal data).
 */
@Component
public class EmailSenderStub implements ChannelSender {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderStub.class);

    @Override
    public AlertChannel channel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public void send(String destination, NotificationMessage message) {
        logger.info("[STUB] Email not sent (no provider yet): to={}, accountId={}, about={}, subject=\"{}\", body=\"{}\"",
            mask(destination), message.accountId(), message.reference(), message.subject(), message.body());
    }

    /** "rahul@example.com" -> "ra***@example.com" */
    static String mask(String email) {
        if (email == null) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }
}
