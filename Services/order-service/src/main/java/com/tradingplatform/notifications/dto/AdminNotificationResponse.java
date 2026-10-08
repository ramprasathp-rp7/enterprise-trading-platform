package com.tradingplatform.notifications.dto;

import java.util.List;

import com.tradingplatform.preferences.AlertChannel;

/**
 * Which of the admin's channels a notification went to (contract: preferences-api.yaml).
 * When {@code channels} contains IN_APP the UI adds the notification to the admin's bell.
 */
public record AdminNotificationResponse(List<AlertChannel> channels) {
}
