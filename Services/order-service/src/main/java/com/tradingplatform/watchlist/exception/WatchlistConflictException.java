package com.tradingplatform.watchlist.exception;

import com.tradingplatform.domain.exception.DomainException;

/** WL-409: duplicate watchlist name for the account, or symbol already in the watchlist */
public final class WatchlistConflictException extends DomainException {

    public WatchlistConflictException(String message) {
        super(com.tradingplatform.domain.enums.ErrorCode.WL_409, message);
    }
}
