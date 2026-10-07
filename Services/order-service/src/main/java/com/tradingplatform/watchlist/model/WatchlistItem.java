package com.tradingplatform.watchlist.model;

import java.time.Instant;

/** One instrument symbol inside a watchlist (row in WATCHLIST_ITEMS). Populated by MyBatis. */
public class WatchlistItem {

    private Long watchlistItemId;
    private Long watchlistId;
    private Long accountId;
    private String symbol;
    private Instant createdAt;

    public WatchlistItem() {
    }

    public WatchlistItem(Long watchlistId, Long accountId, String symbol) {
        this.watchlistId = watchlistId;
        this.accountId = accountId;
        this.symbol = symbol;
    }

    public Long getWatchlistItemId() { return watchlistItemId; }
    public void setWatchlistItemId(Long watchlistItemId) { this.watchlistItemId = watchlistItemId; }

    public Long getWatchlistId() { return watchlistId; }
    public void setWatchlistId(Long watchlistId) { this.watchlistId = watchlistId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
