package com.tradingplatform.watchlist.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingplatform.watchlist.model.WatchlistItem;

/** One instrument in a watchlist. Timestamps are ISO-8601 strings, as watchlist-api.yaml declares. */
public record WatchlistItemResponse(
    Long watchlistItemId,
    Long watchlistId,
    Long accountId,
    String symbol,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt
) {
    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(
            item.getWatchlistItemId(), item.getWatchlistId(), item.getAccountId(), item.getSymbol(), item.getCreatedAt());
    }
}
