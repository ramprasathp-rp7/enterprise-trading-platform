package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.AlertTarget;

import java.time.Instant;
import java.util.List;

/**
 * An admin's preferences as that admin sees them: the stored channels, or the platform default
 * with {@code configured = false}. Targets are in AlertChannel order. AlertTarget redacts its
 * destination in toString, so this record is safe to log.
 */
public record AdminPreferencesView(
        List<AlertTarget> alertChannels,
        boolean configured,
        Instant updatedAt) {

    public AdminPreferencesView {
        alertChannels = List.copyOf(alertChannels);
    }

    static AdminPreferencesView platformDefault() {
        return new AdminPreferencesView(List.of(AlertTarget.inApp()), false, null);
    }
}
