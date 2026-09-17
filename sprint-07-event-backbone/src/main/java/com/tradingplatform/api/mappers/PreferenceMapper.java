package com.tradingplatform.api.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.model.CustomerPreference;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MyBatis mapper for customer preferences persistence.
 * Handles JSONB column storage/retrieval from accounts.preferences.
 *
 * Design:
 * - Preferences are stored as nullable JSONB in the accounts table
 * - NULL means account uses Spring configuration defaults (handled in service layer)
 * - Non-NULL contains per-account preference overrides
 * - Service layer is responsible for merging account preferences with property defaults
 */
@Mapper
public interface PreferenceMapper {

    /**
     * Retrieve preferences for a given account as raw data.
     * Returns the JSON string directly.
     *
     * @param accountId the account ID
     * @return JSON string, empty Optional if not found or preferences is NULL
     */
    @Select("SELECT preferences::text FROM accounts WHERE id = #{accountId} AND preferences IS NOT NULL")
    Optional<String> findPreferencesByAccountIdRaw(@Param("accountId") Long accountId);
    
    /**
     * Retrieve preferences for a given account (even if NULL).
     * Used to distinguish between "no account" and "NULL preferences".
     *
     * @param accountId the account ID
     * @return JSON string, empty Optional if account doesn't exist
     */
    @Select("SELECT preferences::text FROM accounts WHERE id = #{accountId}")
    Optional<String> findPreferencesByAccountIdIncludingNull(@Param("accountId") Long accountId);
    
    /**
     * Retrieve customer preferences for a given account.
     * Uses raw mapper and constructs domain object in interface default method.
     * Returns empty Optional if preferences is NULL (account uses defaults).
     *
     * @param accountId the account ID
     * @return Optional containing CustomerPreference if found with non-null preferences, empty otherwise
     */
    default Optional<CustomerPreference> findPreferencesByAccountId(Long accountId) {
        return findPreferencesByAccountIdRaw(accountId).map(prefsJson -> {
            Map<String, Object> prefs = prefsJson != null && !prefsJson.equals("null") 
                ? parseJson(prefsJson)
                : new HashMap<>();
            // Use current time as lastModified; could be updated to track actual DB timestamp
            return new CustomerPreference(accountId, prefs, LocalDateTime.now());
        });
    }
    
    /**
     * Check if account exists and retrieve preferences (which may be NULL).
     * Returns empty Optional if account doesn't exist.
     *
     * @param accountId the account ID
     * @return Optional with preferences (may be parsed as empty Map if NULL in DB)
     */
    default Optional<Map<String, Object>> getPreferencesForAccount(Long accountId) {
        return findPreferencesByAccountIdIncludingNull(accountId).map(prefsJson -> {
            // If DB has NULL, return empty map (signals to service layer: use defaults)
            return (prefsJson != null && !prefsJson.equals("null")) 
                ? parseJson(prefsJson)
                : new HashMap<>();
        });
    }
    
    /**
     * Parse JSON string to Map. This is a simple JSON parser using ObjectMapper.
     * In a real application, inject ObjectMapper via configuration.
     */
    default Map<String, Object> parseJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * Update preferences for an account.
     * Sets the preferences column in the accounts table.
     * Preferences should be passed as a JSON string.
     *
     * @param accountId the account ID
     * @param preferencesJson the preferences as JSON string (can be null to reset to defaults)
     */
    @Update("UPDATE accounts SET preferences = #{preferencesJson}::jsonb WHERE id = #{accountId}")
    void updatePreferences(@Param("accountId") Long accountId, @Param("preferencesJson") String preferencesJson);

    /**
     * Reset preferences for an account to NULL (will use platform defaults).
     *
     * @param accountId the account ID
     */
    @Update("UPDATE accounts SET preferences = NULL WHERE id = #{accountId}")
    void resetPreferences(@Param("accountId") Long accountId);

    /**
     * Check if an account exists.
     *
     * @param accountId the account ID
     * @return true if account exists, false otherwise
     */
    @Select("SELECT EXISTS(SELECT 1 FROM accounts WHERE id = #{accountId})")
    boolean accountExists(@Param("accountId") Long accountId);
}
