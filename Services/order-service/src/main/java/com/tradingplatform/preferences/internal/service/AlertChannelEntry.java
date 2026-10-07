package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.AlertChannel;

/**
 * One alert channel and its contact detail, as the customer routes send and receive it
 * (AlertChannelEntry in preferences-api.yaml). On the way in it is unvalidated; ContactDetailRules
 * checks and normalises it.
 */
public record AlertChannelEntry(AlertChannel channel, String contactDetail) {

    /** Never prints the contact detail: it is personal data. */
    @Override
    public String toString() {
        return "AlertChannelEntry{channel=" + channel + ", contactDetail="
                + (contactDetail == null ? "none" : "<redacted>") + '}';
    }
}
