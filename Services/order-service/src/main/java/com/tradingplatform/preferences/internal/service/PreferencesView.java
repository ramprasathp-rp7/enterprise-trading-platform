package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.AlertTarget;

import java.time.Instant;
import java.util.List;

/**
 * A customer's preferences as the owning customer sees them: the stored channels, or the platform
 * default with {@code configured = false}. Targets are in AlertChannel order. AlertTarget redacts
 * its destination in toString, so this record is safe to log.
 */
public record PreferencesView(
        long accountId,
        List<AlertTarget> alertChannels,
        boolean configured,
        Instant updatedAt) {

    public PreferencesView {
        alertChannels = List.copyOf(alertChannels);
    }

    static PreferencesView platformDefault(long accountId) {
        return new PreferencesView(accountId, List.of(AlertTarget.inApp()), false, null);
    }
}
