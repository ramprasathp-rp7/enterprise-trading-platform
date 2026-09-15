package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.PreferenceMapper;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InvalidPreferenceException;
import com.tradingplatform.domain.model.CustomerPreference;
import com.tradingplatform.domain.model.PlatformDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing customer preferences.
 * Handles preference retrieval, updates, and merging with platform defaults.
 * Designed to be extended with business logic validation if preferences need to affect trading rules.
 */
@Service
@Transactional
public class PreferenceService {
    private final PreferenceMapper preferenceMapper;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public PreferenceService(PreferenceMapper preferenceMapper, AccountService accountService, ObjectMapper objectMapper) {
        this.preferenceMapper = Objects.requireNonNull(preferenceMapper, "preferenceMapper cannot be null");
        this.accountService = Objects.requireNonNull(accountService, "accountService cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Retrieve merged preferences for an account.
     * Account-specific preferences override platform defaults.
     *
     * @param accountId the account ID
     * @return merged preferences (account-level + platform defaults)
     * @throws AccountNotFoundException if account does not exist
     */
    public Map<String, Object> getPreferencesForAccount(Long accountId) {
        // Verify account exists
        accountService.getAccount(accountId);

        // Get platform defaults
        Map<String, Object> mergedPreferences = getPlatformDefaults();

        // Merge with account-specific preferences
        preferenceMapper.findPreferencesByAccountId(accountId).ifPresent(pref ->
                mergedPreferences.putAll(pref.getPreferences())
        );

        return mergedPreferences;
    }

    /**
     * Update customer preferences for an account.
     * Validates preference keys and performs partial update (only provided keys are updated).
     * Non-provided keys retain their current values or platform defaults.
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

        // Get existing preferences or start fresh
        Map<String, Object> existingPreferences = preferenceMapper.findPreferencesByAccountId(accountId)
                .map(pref -> new HashMap<>(pref.getPreferences()))
                .orElseGet(HashMap::new);

        // Validate new preferences before merging
        validatePreferences(preferences);

        // Merge new preferences with existing ones (partial update)
        existingPreferences.putAll(preferences);

        // Convert to JSON string and persist
        try {
            String preferencesJson = objectMapper.writeValueAsString(existingPreferences);
            preferenceMapper.upsertPreferences(accountId, preferencesJson);
        } catch (Exception e) {
            throw new InvalidPreferenceException("Failed to serialize preferences: " + e.getMessage());
        }
    }

    /**
     * Delete customer preferences for an account, resetting to platform defaults.
     *
     * @param accountId the account ID
     * @throws AccountNotFoundException if account does not exist
     */
    public void deleteAccountPreferences(Long accountId) {
        // Verify account exists
        accountService.getAccount(accountId);

        preferenceMapper.deletePreferences(accountId);
    }

    /**
     * Retrieve platform-wide default preferences.
     *
     * @return merged platform defaults
     */
    public Map<String, Object> getPlatformDefaults() {
        return preferenceMapper.findPlatformDefaults()
                .map(PlatformDefaults::getDefaults)
                .map(HashMap::new)
                .orElseGet(HashMap::new);
    }

    /**
     * Get customer preference with last modified timestamp.
     * Used internally for response DTOs.
     *
     * @param accountId the account ID
     * @return Optional containing CustomerPreference if found
     */
    @Transactional(readOnly = true)
    public Optional<CustomerPreference> getCustomerPreference(Long accountId) {
        return preferenceMapper.findPreferencesByAccountId(accountId);
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
