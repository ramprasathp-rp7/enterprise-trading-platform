package com.tradingplatform.preferences;

/**
 * Where the platform reaches a customer about their trades and alerts.
 *
 * The set is closed on purpose: there is no webhook or URL channel, so a customer can never make
 * the platform post to a destination of their choosing.
 */
public enum AlertChannel {
    /** Email address held (encrypted) by the preferences module. */
    EMAIL,
    /** E.164 telephone number held (encrypted) by the preferences module. */
    SMS,
    /** The platform's own notification inbox. Needs no contact detail; the platform default. */
    IN_APP
}
