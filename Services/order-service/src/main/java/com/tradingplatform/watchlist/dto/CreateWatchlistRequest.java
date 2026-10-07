package com.tradingplatform.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/v1/accounts/{accountId}/watchlists */
public record CreateWatchlistRequest(
    @NotBlank(message = "name cannot be blank")
    @Size(max = 60, message = "name must be at most 60 characters")
    String name
) {
}
