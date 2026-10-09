package com.tradingplatform.notifications.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.notifications.dto.NotificationHistoryItem;
import com.tradingplatform.notifications.service.NotificationHistoryService;

/**
 * The account's notification history for the Notifications page (order outcomes and price alerts).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/notification-history")
public class NotificationHistoryController {

    private final NotificationHistoryService historyService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public NotificationHistoryController(NotificationHistoryService historyService,
                                         AccountAccessAuthorizer accountAccessAuthorizer) {
        this.historyService = historyService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    /**
     * GET /api/v1/accounts/{accountId}/notification-history?limit=
     * Newest first, every channel. limit defaults to 100, max 200.
     */
    @GetMapping
    public ResponseEntity<List<NotificationHistoryItem>> getNotificationHistory(
            @PathVariable long accountId,
            @RequestParam(required = false) Integer limit) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(historyService.history(accountId, limit));
    }
}
