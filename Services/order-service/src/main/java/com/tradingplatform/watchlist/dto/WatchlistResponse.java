package com.tradingplatform.watchlist.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingplatform.watchlist.model.Watchlist;

/** A watchlist with its items, oldest item first. */
public record WatchlistResponse(
    Long watchlistId,
    Long accountId,
    String name,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant updatedAt,
    List<WatchlistItemResponse> items
) {
    public static WatchlistResponse from(Watchlist watchlist, List<WatchlistItemResponse> items) {
        return new WatchlistResponse(
            watchlist.getWatchlistId(), watchlist.getAccountId(), watchlist.getName(),
            watchlist.getCreatedAt(), watchlist.getUpdatedAt(), items);
    }
}
