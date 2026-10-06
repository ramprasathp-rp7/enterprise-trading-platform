package com.tradingplatform.preferences;

/**
 * Raised by {@link PreferenceResolver} when the preference store cannot be read and there is no
 * last known answer for the account. It is never raised because a customer has not chosen a
 * channel: that case answers the platform default.
 *
 * Caller contract: do not drop the message and do not guess a channel. Keep the notification
 * queued and retry, so the customer still hears about their trade once the store answers.
 */
public class PreferencesUnavailableException extends RuntimeException {

    private final long accountId;

    public PreferencesUnavailableException(long accountId, Throwable cause) {
        super("Preferences unavailable for accountId=" + accountId, cause);
        this.accountId = accountId;
    }

    public long getAccountId() {
        return accountId;
    }
}
