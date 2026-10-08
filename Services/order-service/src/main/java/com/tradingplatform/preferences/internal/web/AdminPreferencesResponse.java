package com.tradingplatform.preferences.internal.web;

import com.tradingplatform.preferences.internal.service.AdminPreferencesView;
import com.tradingplatform.preferences.internal.service.AlertChannelEntry;

import java.time.Instant;
import java.util.List;

/** Response of the admin preferences routes (contract: preferences-api.yaml). */
public record AdminPreferencesResponse(
        List<AlertChannelEntry> alertChannels,
        boolean configured,
        Instant updatedAt) {

    static AdminPreferencesResponse from(AdminPreferencesView view) {
        return new AdminPreferencesResponse(
                view.alertChannels().stream().map(t -> new AlertChannelEntry(t.channel(), t.destination())).toList(),
                view.configured(),
                view.updatedAt());
    }
}
