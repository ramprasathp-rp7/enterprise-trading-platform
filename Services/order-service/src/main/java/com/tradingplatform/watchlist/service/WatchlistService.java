package com.tradingplatform.watchlist.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.watchlist.mapper.WatchlistMapper;
import com.tradingplatform.watchlist.dto.WatchlistItemResponse;
import com.tradingplatform.watchlist.dto.WatchlistResponse;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.watchlist.exception.WatchlistConflictException;
import com.tradingplatform.watchlist.exception.WatchlistNotFoundException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.watchlist.model.Watchlist;
import com.tradingplatform.watchlist.model.WatchlistItem;

/**
 * Watchlists: named lists of instrument symbols per account.
 *
 * Rules:
 * - watchlist names are trimmed and unique per account, ignoring case (WL-409);
 * - a symbol must exist in INSTRUMENTS (INS-404) and appears at most once per watchlist (WL-409);
 *   untradable instruments may still be watched;
 * - a watchlist or item belonging to another account is reported as not found (WL-404).
 */
@Service
public class WatchlistService {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistService.class);
    static final int MAX_NAME_LENGTH = 60;

    private final WatchlistMapper watchlistMapper;
    private final InstrumentMapper instrumentMapper;

    public WatchlistService(WatchlistMapper watchlistMapper, InstrumentMapper instrumentMapper) {
        this.watchlistMapper = watchlistMapper;
        this.instrumentMapper = instrumentMapper;
    }

    /** All watchlists of an account with their items, oldest list first */
    @Transactional(readOnly = true)
    public List<WatchlistResponse> getWatchlists(long accountId) {
        Map<Long, List<WatchlistItemResponse>> itemsByList = watchlistMapper.findItemsByAccountId(accountId).stream()
            .map(WatchlistItemResponse::from)
            .collect(Collectors.groupingBy(WatchlistItemResponse::watchlistId));

        return watchlistMapper.findByAccountId(accountId).stream()
            .map(w -> WatchlistResponse.from(w, itemsByList.getOrDefault(w.getWatchlistId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public WatchlistResponse getWatchlist(long accountId, long watchlistId) {
        return toResponse(requireWatchlist(accountId, watchlistId));
    }

    @Transactional
    public WatchlistResponse createWatchlist(long accountId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidOrderException("Watchlist name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        if (watchlistMapper.existsByName(accountId, name)) {
            throw new WatchlistConflictException("A watchlist named '" + name + "' already exists");
        }

        Watchlist watchlist = new Watchlist(accountId, name);
        try {
            watchlistMapper.insertWatchlist(watchlist);
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent create of the same name
            throw new WatchlistConflictException("A watchlist named '" + name + "' already exists");
        }
        logger.info("Created watchlist: accountId={}, watchlistId={}", accountId, watchlist.getWatchlistId());
        return toResponse(requireWatchlist(accountId, watchlist.getWatchlistId()));
    }

    @Transactional
    public void deleteWatchlist(long accountId, long watchlistId) {
        if (watchlistMapper.deleteWatchlist(watchlistId, accountId) == 0) {
            throw new WatchlistNotFoundException(watchlistId);
        }
        logger.info("Deleted watchlist: accountId={}, watchlistId={}", accountId, watchlistId);
    }

    @Transactional
    public WatchlistItemResponse addItem(long accountId, long watchlistId, String rawSymbol) {
        requireWatchlist(accountId, watchlistId);

        String requested = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        Instrument instrument = requested.isEmpty() ? null : instrumentMapper.findBySymbol(requested);
        if (instrument == null) {
            throw new InstrumentNotFoundException(requested);
        }
        String symbol = instrument.getSymbol();
        if (watchlistMapper.itemExists(watchlistId, symbol)) {
            throw new WatchlistConflictException(symbol + " is already in this watchlist");
        }

        WatchlistItem item = new WatchlistItem(watchlistId, accountId, symbol);
        try {
            watchlistMapper.insertItem(item);
        } catch (DuplicateKeyException e) {
            throw new WatchlistConflictException(symbol + " is already in this watchlist");
        }
        watchlistMapper.touch(watchlistId);
        logger.info("Added {} to watchlist: accountId={}, watchlistId={}", symbol, accountId, watchlistId);
        return WatchlistItemResponse.from(watchlistMapper.findItemById(item.getWatchlistItemId()));
    }

    @Transactional
    public void removeItem(long accountId, long watchlistId, long watchlistItemId) {
        requireWatchlist(accountId, watchlistId);
        if (watchlistMapper.deleteItem(watchlistItemId, watchlistId, accountId) == 0) {
            throw new WatchlistNotFoundException(watchlistId, "Watchlist item not found");
        }
        watchlistMapper.touch(watchlistId);
        logger.info("Removed item {} from watchlist: accountId={}, watchlistId={}", watchlistItemId, accountId, watchlistId);
    }

    private Watchlist requireWatchlist(long accountId, long watchlistId) {
        Watchlist watchlist = watchlistMapper.findById(watchlistId, accountId);
        if (watchlist == null) {
            throw new WatchlistNotFoundException(watchlistId);
        }
        return watchlist;
    }

    private WatchlistResponse toResponse(Watchlist watchlist) {
        List<WatchlistItemResponse> items = watchlistMapper.findItemsByWatchlistId(watchlist.getWatchlistId()).stream()
            .map(WatchlistItemResponse::from)
            .toList();
        return WatchlistResponse.from(watchlist, items);
    }
}
