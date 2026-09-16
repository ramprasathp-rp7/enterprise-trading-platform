package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.PreferenceMapper;
import com.tradingplatform.config.PreferenceDefaults;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InvalidPreferenceException;
import com.tradingplatform.domain.model.CustomerPreference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing customer preferences.
 * Handles preference retrieval, updates, and merging with Spring configuration defaults.
 *
 * Architecture:
 * - Preferences stored in accounts.preferences as nullable JSONB
 * - NULL preferences mean account inherits all Spring config defaults
 * - Non-null preferences are merged with config defaults (preferences override)
 * - Spring configuration (@see PreferenceDefaults) provides environment-specific defaults
 */
@Service
@Transactional
public class PreferenceService implements IPreferenceService {
    private final PreferenceMapper preferenceMapper;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final PreferenceDefaults preferenceDefaults;

    public PreferenceService(PreferenceMapper preferenceMapper, AccountService accountService, 
                           ObjectMapper objectMapper, PreferenceDefaults preferenceDefaults) {
        this.preferenceMapper = Objects.requireNonNull(preferenceMapper, "preferenceMapper cannot be null");
        this.accountService = Objects.requireNonNull(accountService, "accountService cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.preferenceDefaults = Objects.requireNonNull(preferenceDefaults, "preferenceDefaults cannot be null");
    }

    /**
     * Retrieve merged preferences for an account.
     * Returns Spring configuration defaults, with account-specific preferences overriding them.
     *
     * @param accountId the account ID
     * @return merged preferences (account-level preferences override config defaults)
     * @throws AccountNotFoundException if account does not exist
     */
    public Map<String, Object> getPreferencesForAccount(Long accountId) {
        // Verify account exists
        accountService.getAccount(accountId);

        // Start with Spring configuration defaults (a copy to prevent external modification)
        Map<String, Object> mergedPreferences = preferenceDefaults.getDefaultsCopy();

        // Merge with account-specific preferences if they exist (account overrides defaults)
        preferenceMapper.getPreferencesForAccount(accountId).ifPresent(accountPrefs -> {
            if (!accountPrefs.isEmpty()) {
                mergedPreferences.putAll(accountPrefs);
            }
        });

        return mergedPreferences;
    }

    /**
     * Update customer preferences for an account.
     * Validates preference keys and performs partial update (only provided keys are updated).
     * Non-provided keys retain their current value or use the default.
     *
     * @param accountId the account ID
     * @param preferences the preference key-value pairs to update
     * @throws AccountNotFoundException if account does not exist
     * @throws InvalidPreferenceException if preference validation fails
     */
    public void updateAccountPreferences(Long accountId, Map<String, Object> preferences) {
        // Verify account exists
        accountService.getAccount(accountId);

        if (preferences == null || preferences.isEmpty()) {
            throw new InvalidPreferenceException("Preferences cannot be null or empty");
        }

        // Validate new preferences before merging
        validatePreferences(preferences);

        // Get existing account preferences (may be empty if NULL in DB)
        Map<String, Object> accountPreferences = preferenceMapper.getPreferencesForAccount(accountId)
                .orElseGet(HashMap::new);

        // Merge new preferences with existing ones (partial update)
        accountPreferences.putAll(preferences);

        // Convert to JSON string and persist
        try {
            String preferencesJson = objectMapper.writeValueAsString(accountPreferences);
            preferenceMapper.updatePreferences(accountId, preferencesJson);
        } catch (Exception e) {
            throw new InvalidPreferenceException("Failed to serialize preferences: " + e.getMessage());
        }
    }

    /**
     * Delete customer preferences for an account, resetting to Spring config defaults.
     * This sets the preferences column to NULL in the database.
     *
     * @param accountId the account ID
     * @throws AccountNotFoundException if account does not exist
     */
    public void deleteAccountPreferences(Long accountId) {
        // Verify account exists
        accountService.getAccount(accountId);

        preferenceMapper.resetPreferences(accountId);
    }

    /**
     * Retrieve platform-wide default preferences from Spring configuration.
     * This does not query the database; it returns the configured defaults.
     *
     * @return a copy of platform defaults from Spring configuration
     */
    public Map<String, Object> getPlatformDefaults() {
        return preferenceDefaults.getDefaultsCopy();
    }

    /**
     * Get customer preference with merged result.
     * Includes account-specific preferences merged with defaults.
     * Used internally for response DTOs.
     *
     * @param accountId the account ID
     * @return Optional containing merged preferences
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerPreferencesMerged(Long accountId) {
        return getPreferencesForAccount(accountId);
    }

    /**
     * Validate preference keys and values.
     * Can be extended to enforce stricter validation rules or type checking.
     *
     * @param preferences the preferences to validate
     * @throws InvalidPreferenceException if validation fails
     */
    private void validatePreferences(Map<String, Object> preferences) {
        // Basic validation: check for null values and empty keys
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new InvalidPreferenceException("Preference key cannot be null or empty");
            }

            // Optional: Add type-specific validations here
            // For example: if (entry.getKey().equals("TIMEZONE")) { validateTimezone(entry.getValue()); }
        }

        // TODO: Extend with specific preference validations:
        // - TIMEZONE: Must be a valid ZoneId
        // - TRADING_MAX_ORDER_SIZE: Must be positive number
        // - LANGUAGE: Must be valid locale code
        // - etc.
    }
}
