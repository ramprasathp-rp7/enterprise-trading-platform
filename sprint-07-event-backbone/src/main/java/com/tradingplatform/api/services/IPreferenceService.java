package com.tradingplatform.api.services;

import java.util.Map;

/**
 * Interface for preference service operations.
 * Enables easier testing with stub implementations and future alternative implementations.
 */
public interface IPreferenceService {
    
    /**
     * Retrieve merged preferences for an account.
     * Returns Spring configuration defaults, with account-specific preferences overriding them.
     *
     * @param accountId the account ID
     * @return merged preferences (account-level preferences override config defaults)
     */
    Map<String, Object> getPreferencesForAccount(Long accountId);
    
    /**
     * Update account preferences with partial updates.
     * Merges provided preferences with existing account preferences.
     *
     * @param accountId the account ID
     * @param preferences the preferences to update
     */
    void updateAccountPreferences(Long accountId, Map<String, Object> preferences);
    
    /**
     * Delete (reset) account preferences, reverting to defaults.
     *
     * @param accountId the account ID
     */
    void deleteAccountPreferences(Long accountId);
    
    /**
     * Get platform defaults from Spring configuration.
     *
     * @return map of default preferences
     */
    Map<String, Object> getPlatformDefaults();
}
