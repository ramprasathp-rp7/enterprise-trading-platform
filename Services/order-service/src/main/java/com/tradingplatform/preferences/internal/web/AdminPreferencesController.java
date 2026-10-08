package com.tradingplatform.preferences.internal.web;

import com.tradingplatform.api.security.AdminIdentity;
import com.tradingplatform.preferences.internal.service.AdminPreferencesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin routes for the signed-in admin's own preferences (contract: preferences-api.yaml).
 *
 * Under /api/v1/admin/**, so the security chain has already required ROLE_ADMIN. There is no id in
 * the path: the admin is always the token's subject, so one admin cannot read or change another's
 * contact details.
 */
@RestController
@RequestMapping("/api/v1/admin/preferences")
public class AdminPreferencesController {

    private final AdminIdentity adminIdentity;
    private final AdminPreferencesService preferencesService;

    public AdminPreferencesController(AdminIdentity adminIdentity, AdminPreferencesService preferencesService) {
        this.adminIdentity = adminIdentity;
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public ResponseEntity<AdminPreferencesResponse> getAdminPreferences() {
        return ResponseEntity.ok(AdminPreferencesResponse.from(
                preferencesService.get(adminIdentity.currentAdminUserId())));
    }

    @PutMapping
    public ResponseEntity<AdminPreferencesResponse> updateAdminPreferences(@RequestBody PreferencesRequest request) {
        return ResponseEntity.ok(AdminPreferencesResponse.from(
                preferencesService.replace(adminIdentity.currentAdminUserId(), request.alertChannels())));
    }

    @DeleteMapping
    public ResponseEntity<Void> resetAdminPreferences() {
        preferencesService.reset(adminIdentity.currentAdminUserId());
        return ResponseEntity.noContent().build();
    }
}
