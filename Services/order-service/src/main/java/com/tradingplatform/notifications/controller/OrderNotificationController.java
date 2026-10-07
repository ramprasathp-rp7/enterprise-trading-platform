package com.tradingplatform.notifications.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.notifications.dto.OrderNotificationResponse;
import com.tradingplatform.notifications.service.OrderNotificationService;

/**
 * Order notifications for the UI bell, built from the trade-events topic.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/order-notifications")
public class OrderNotificationController {

    private final OrderNotificationService notificationService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public OrderNotificationController(OrderNotificationService notificationService,
                                       AccountAccessAuthorizer accountAccessAuthorizer) {
        this.notificationService = notificationService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    /**
     * GET /api/v1/accounts/{accountId}/order-notifications
     * Order outcomes for the account, oldest first.
     * Optional query parameters:
     *   - after: Long - only notifications with a larger id (the caller's cursor); omit for the latest
     *   - limit: Integer - at most this many (default 50, max 200)
     */
    @GetMapping
    public ResponseEntity<List<OrderNotificationResponse>> getOrderNotifications(
            @PathVariable long accountId,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        List<OrderNotificationResponse> body = notificationService.list(accountId, after, limit).stream()
            .map(OrderNotificationResponse::from)
            .toList();
        return ResponseEntity.ok(body);
    }
}
