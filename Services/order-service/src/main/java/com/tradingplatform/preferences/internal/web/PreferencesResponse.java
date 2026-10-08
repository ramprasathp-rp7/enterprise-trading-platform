package com.tradingplatform.preferences.internal.web;

import com.tradingplatform.preferences.internal.service.AlertChannelEntry;
import com.tradingplatform.preferences.internal.service.PreferencesView;

import java.time.Instant;
import java.util.List;

/** Response of the preferences routes (contract: preferences-api.yaml). */
public record PreferencesResponse(
        long accountId,
        List<AlertChannelEntry> alertChannels,
        boolean configured,
        Instant updatedAt) {

    static PreferencesResponse from(PreferencesView view) {
        return new PreferencesResponse(
                view.accountId(),
                view.alertChannels().stream().map(t -> new AlertChannelEntry(t.channel(), t.destination())).toList(),
                view.configured(),
                view.updatedAt());
    }
}
