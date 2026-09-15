package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.PreferenceService;
import com.tradingplatform.domain.dto.PreferenceRequest;
import com.tradingplatform.domain.dto.PreferenceResponse;
import com.tradingplatform.domain.dto.PlatformDefaultsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST Controller for customer preference management.
 * Endpoints:
 * - GET /api/v1/accounts/{id}/preferences - Retrieve merged preferences
 * - PUT /api/v1/accounts/{id}/preferences - Update account preferences
 * - DELETE /api/v1/accounts/{id}/preferences - Reset to platform defaults
 * - GET /api/v1/preferences/defaults - Retrieve platform defaults
 *
 * All account-level endpoints enforce account-level authorization via AccountAccessAuthorizer.
 */
@RestController
@RequestMapping("/api/v1")
public class PreferenceController {
    private final PreferenceService preferenceService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public PreferenceController(PreferenceService preferenceService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.preferenceService = preferenceService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    /**
     * Retrieve merged preferences for an account (account-specific + platform defaults).
     *
     * @param id the account ID
     * @return PreferenceResponse with merged preferences
     */
    @GetMapping("/accounts/{id}/preferences")
    public ResponseEntity<PreferenceResponse> getPreferences(@PathVariable Long id) {
        // Enforce account-level authorization
        accountAccessAuthorizer.assertTokenCanAccess(id);

        Map<String, Object> mergedPreferences = preferenceService.getPreferencesForAccount(id);

        // Get last modified timestamp from account preferences if they exist
        LocalDateTime lastModified = preferenceService.getCustomerPreference(id)
                .map(pref -> pref.getLastModified())
                .orElseGet(LocalDateTime::now);

        PreferenceResponse response = new PreferenceResponse(id, mergedPreferences, lastModified);
        return ResponseEntity.ok(response);
    }

    /**
     * Update account preferences (partial update - only provided keys are modified).
     * Returns the merged preferences after update.
     *
     * @param id the account ID
     * @param request the preferences to update
     * @return PreferenceResponse with updated merged preferences
     */
    @PutMapping("/accounts/{id}/preferences")
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @PathVariable Long id,
            @RequestBody PreferenceRequest request) {
        // Enforce account-level authorization
        accountAccessAuthorizer.assertTokenCanAccess(id);

        // Update preferences
        preferenceService.updateAccountPreferences(id, request.getPreferences());

        // Retrieve and return updated merged preferences
        Map<String, Object> updatedPreferences = preferenceService.getPreferencesForAccount(id);
        LocalDateTime lastModified = preferenceService.getCustomerPreference(id)
                .map(pref -> pref.getLastModified())
                .orElseGet(LocalDateTime::now);

        PreferenceResponse response = new PreferenceResponse(id, updatedPreferences, lastModified);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete account preferences, resetting the account to use platform defaults.
     *
     * @param id the account ID
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/accounts/{id}/preferences")
    public ResponseEntity<Void> deletePreferences(@PathVariable Long id) {
        // Enforce account-level authorization
        accountAccessAuthorizer.assertTokenCanAccess(id);

        // Delete account preferences
        preferenceService.deleteAccountPreferences(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieve platform-wide default preferences (public endpoint, no account authorization required).
     *
     * @return PlatformDefaultsResponse with platform defaults
     */
    @GetMapping("/preferences/defaults")
    public ResponseEntity<PlatformDefaultsResponse> getPlatformDefaults() {
        Map<String, Object> defaults = preferenceService.getPlatformDefaults();
        PlatformDefaultsResponse response = new PlatformDefaultsResponse(defaults);
        return ResponseEntity.ok(response);
    }
}
