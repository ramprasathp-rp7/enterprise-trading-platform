package com.tradingplatform.preferences;

import java.util.Objects;

/**
 * One place to deliver a notification: a channel and, for EMAIL and SMS, its address.
 *
 * @param channel     never null
 * @param destination the decrypted email address or E.164 number; null for IN_APP
 */
public record AlertTarget(AlertChannel channel, String destination) {

    public AlertTarget {
        Objects.requireNonNull(channel, "channel cannot be null");
        if ((channel == AlertChannel.IN_APP) != (destination == null)) {
            throw new IllegalArgumentException("IN_APP has no destination; EMAIL and SMS require one");
        }
    }

    public static AlertTarget inApp() {
        return new AlertTarget(AlertChannel.IN_APP, null);
    }

    /** Never prints the destination: it is personal data and this object ends up in logs. */
    @Override
    public String toString() {
        return "AlertTarget{channel=" + channel
                + ", destination=" + (destination == null ? "none" : "<redacted>") + '}';
    }
}
