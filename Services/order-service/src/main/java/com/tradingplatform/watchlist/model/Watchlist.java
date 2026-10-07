package com.tradingplatform.watchlist.model;

import java.time.Instant;

/** A named list of instruments owned by one account (row in WATCHLISTS). Populated by MyBatis. */
public class Watchlist {

    private Long watchlistId;
    private String name;
    private Long accountId;
    private Instant createdAt;
    private Instant updatedAt;

    public Watchlist() {
    }

    public Watchlist(Long accountId, String name) {
        this.accountId = accountId;
        this.name = name;
    }

    public Long getWatchlistId() { return watchlistId; }
    public void setWatchlistId(Long watchlistId) { this.watchlistId = watchlistId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
