package com.tradingplatform.watchlist.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.watchlist.service.WatchlistService;
import com.tradingplatform.watchlist.dto.AddWatchlistItemRequest;
import com.tradingplatform.watchlist.dto.CreateWatchlistRequest;
import com.tradingplatform.watchlist.dto.WatchlistItemResponse;
import com.tradingplatform.watchlist.dto.WatchlistResponse;

import jakarta.validation.Valid;

/**
 * Watchlist endpoints (contract: watchlist-api.yaml).
 * - GET    /api/v1/accounts/{accountId}/watchlists
 * - POST   /api/v1/accounts/{accountId}/watchlists
 * - GET    /api/v1/accounts/{accountId}/watchlists/{watchlistId}
 * - DELETE /api/v1/accounts/{accountId}/watchlists/{watchlistId}
 * - POST   /api/v1/accounts/{accountId}/watchlists/{watchlistId}/items
 * - DELETE /api/v1/accounts/{accountId}/watchlists/{watchlistId}/items/{watchlistItemId}
 *
 * Every endpoint checks that the token's accountId claim matches the path (ACC-403).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public WatchlistController(WatchlistService watchlistService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.watchlistService = watchlistService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @GetMapping
    public ResponseEntity<List<WatchlistResponse>> getWatchlists(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(watchlistService.getWatchlists(accountId));
    }

    @PostMapping
    public ResponseEntity<WatchlistResponse> createWatchlist(
            @PathVariable long accountId,
            @Valid @RequestBody CreateWatchlistRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(watchlistService.createWatchlist(accountId, request.name()));
    }

    @GetMapping("/{watchlistId}")
    public ResponseEntity<WatchlistResponse> getWatchlist(@PathVariable long accountId, @PathVariable long watchlistId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(watchlistService.getWatchlist(accountId, watchlistId));
    }

    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<Void> deleteWatchlist(@PathVariable long accountId, @PathVariable long watchlistId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        watchlistService.deleteWatchlist(accountId, watchlistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{watchlistId}/items")
    public ResponseEntity<WatchlistItemResponse> addItem(
            @PathVariable long accountId,
            @PathVariable long watchlistId,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(watchlistService.addItem(accountId, watchlistId, request.symbol()));
    }

    @DeleteMapping("/{watchlistId}/items/{watchlistItemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable long accountId,
            @PathVariable long watchlistId,
            @PathVariable long watchlistItemId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        watchlistService.removeItem(accountId, watchlistId, watchlistItemId);
        return ResponseEntity.noContent().build();
    }
}
