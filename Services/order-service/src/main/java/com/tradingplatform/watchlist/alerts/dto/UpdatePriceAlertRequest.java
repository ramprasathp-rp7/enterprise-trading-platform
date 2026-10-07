package com.tradingplatform.watchlist.alerts.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for PATCH /api/v1/accounts/{accountId}/price-alerts/{alertId}: false pauses, true resumes. */
public record UpdatePriceAlertRequest(
    @NotNull(message = "active is required")
    Boolean active
) {
}
