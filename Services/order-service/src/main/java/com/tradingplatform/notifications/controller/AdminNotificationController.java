package com.tradingplatform.notifications.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.security.AdminIdentity;
import com.tradingplatform.notifications.dto.AdminNotificationResponse;
import com.tradingplatform.notifications.dto.CustomerRegisteredRequest;
import com.tradingplatform.notifications.service.AdminNotificationService;

import jakarta.validation.Valid;

/**
 * Admin notifications, delivered on the signed-in admin's chosen channels.
 *
 * Under /api/v1/admin/**, so the security chain has already required ROLE_ADMIN. The notice only
 * ever goes to the admin making the call (the token's subject), never to an address in the request.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final AdminIdentity adminIdentity;
    private final AdminNotificationService notificationService;

    public AdminNotificationController(AdminIdentity adminIdentity, AdminNotificationService notificationService) {
        this.adminIdentity = adminIdentity;
        this.notificationService = notificationService;
    }

    /**
     * POST /api/v1/admin/notifications/customer-registered
     * Called by the admin UI after the auth service has registered a customer.
     */
    @PostMapping("/customer-registered")
    public ResponseEntity<AdminNotificationResponse> customerRegistered(@Valid @RequestBody CustomerRegisteredRequest request) {
        return ResponseEntity.ok(new AdminNotificationResponse(notificationService.customerRegistered(
                adminIdentity.currentAdminUserId(), request.username(), request.accountId())));
    }
}
