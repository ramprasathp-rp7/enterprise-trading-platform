package com.tradingplatform.notifications.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Body of POST /api/v1/admin/notifications/customer-registered (contract: preferences-api.yaml).
 * The username rules match the auth service's registration rules, so nothing else can be
 * smuggled into the email or SMS text.
 */
public record CustomerRegisteredRequest(
        @NotNull @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$") String username,
        @NotNull @Positive Long accountId) {
}
