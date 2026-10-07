package com.tradingplatform.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/v1/accounts/{accountId}/watchlists/{watchlistId}/items */
public record AddWatchlistItemRequest(
    @NotBlank(message = "symbol cannot be blank")
    @Size(max = 20, message = "symbol must be at most 20 characters")
    String symbol
) {
}
