package com.tradingplatform.watchlist.alerts.dto;

import java.math.BigDecimal;

import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/v1/accounts/{accountId}/price-alerts */
public record CreatePriceAlertRequest(
    @NotBlank(message = "symbol cannot be blank")
    @Size(max = 20, message = "symbol must be at most 20 characters")
    String symbol,

    @NotNull(message = "direction is required")
    AlertDirection direction,

    @NotNull(message = "threshold is required")
    @DecimalMin(value = "0", inclusive = false, message = "threshold must be greater than 0")
    BigDecimal threshold,

    @NotNull(message = "mode is required")
    AlertMode mode
) {
}
