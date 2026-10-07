package com.tradingplatform.watchlist.exception;

import com.tradingplatform.domain.exception.DomainException;

/** WL-404: the watchlist, or the item inside it, does not exist for this account */
public final class WatchlistNotFoundException extends DomainException {

    private final Long watchlistId;

    public WatchlistNotFoundException(Long watchlistId, String message) {
        super(com.tradingplatform.domain.enums.ErrorCode.WL_404, message);
        this.watchlistId = watchlistId;
    }

    public WatchlistNotFoundException(Long watchlistId) {
        this(watchlistId, "Watchlist not found");
    }

    public Long getWatchlistId() {
        return watchlistId;
    }
}
