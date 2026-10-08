package com.tradingplatform.preferences.internal.web;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.preferences.internal.service.PreferencesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer routes for preferences (contract: preferences-api.yaml).
 *
 * Every route sits under /api/v1/, so the service has verified the token before this runs, and
 * every route compares the token's accountId claim with the path before touching data: another
 * account's preferences are ACC-403, never an empty answer.
 *
 * The resolution path notifications uses is not here. It is the PreferenceResolver Java
 * interface, and no controller depends on it (enforced by PreferencesModuleBoundaryTest).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/preferences")
public class PreferencesController {

    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final PreferencesService preferencesService;

    public PreferencesController(AccountAccessAuthorizer accountAccessAuthorizer,
                                 PreferencesService preferencesService) {
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public ResponseEntity<PreferencesResponse> getPreferences(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(PreferencesResponse.from(preferencesService.get(accountId)));
    }

    @PutMapping
    public ResponseEntity<PreferencesResponse> updatePreferences(@PathVariable long accountId,
                                                                 @RequestBody PreferencesRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(PreferencesResponse.from(
                preferencesService.replace(accountId, request.alertChannels())));
    }

    @DeleteMapping
    public ResponseEntity<Void> resetPreferences(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        preferencesService.reset(accountId);
        return ResponseEntity.noContent().build();
    }
}
