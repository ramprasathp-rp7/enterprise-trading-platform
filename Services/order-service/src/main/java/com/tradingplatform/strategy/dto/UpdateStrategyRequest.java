package com.tradingplatform.strategy.dto;

import jakarta.validation.constraints.NotNull;

/** Turns a strategy off (immediately, even mid-cycle) or back on */
public record UpdateStrategyRequest(
    @NotNull(message = "active is required")
    Boolean active
) {
}
