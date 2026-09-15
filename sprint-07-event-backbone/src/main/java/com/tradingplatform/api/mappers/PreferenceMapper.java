package com.tradingplatform.api.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.model.CustomerPreference;
import com.tradingplatform.domain.model.PlatformDefaults;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MyBatis mapper for customer preferences and platform defaults persistence.
 * Handles JSONB column storage/retrieval. JSON serialization/deserialization
 * is handled in the Service layer using ObjectMapper.
 */
@Mapper
public interface PreferenceMapper {

    /**
     * Retrieve customer preferences for a given account as raw data.
     * Returns a map with keys: account_id, preferences (as JSON string), last_modified.
     *
     * @param accountId the account ID
     * @return Map with result data, empty Optional if not found
     */
    @Select("SELECT account_id, preferences::text as preferences, last_modified FROM customer_preferences WHERE account_id = #{accountId}")
    Optional<Map<String, Object>> findPreferencesByAccountIdRaw(@Param("accountId") Long accountId);
    
    /**
     * Retrieve customer preferences for a given account.
     * Uses raw mapper and constructs domain object in interface default method.
     *
     * @param accountId the account ID
     * @return Optional containing CustomerPreference if found, empty otherwise
     */
    default Optional<CustomerPreference> findPreferencesByAccountId(Long accountId) {
        return findPreferencesByAccountIdRaw(accountId).map(row -> {
            Long id = ((Number) row.get("account_id")).longValue();
            String prefsJson = (String) row.get("preferences");
            Map<String, Object> prefs = prefsJson != null && !prefsJson.equals("null") 
                ? parseJson(prefsJson)
                : new HashMap<>();
            LocalDateTime modified = (LocalDateTime) row.get("last_modified");
            return new CustomerPreference(id, prefs, modified);
        });
    }

    /**
     * Retrieve platform-wide default preferences as raw data.
     *
     * @return Map with result data, empty Optional if not found
     */
    @Select("SELECT id, defaults::text as defaults, last_updated FROM platform_defaults WHERE id = 1")
    Optional<Map<String, Object>> findPlatformDefaultsRaw();
    
    /**
     * Retrieve platform-wide default preferences.
     * Uses raw mapper and constructs domain object in interface default method.
     *
     * @return Optional containing PlatformDefaults if found, empty otherwise
     */
    default Optional<PlatformDefaults> findPlatformDefaults() {
        return findPlatformDefaultsRaw().map(row -> {
            Long id = ((Number) row.get("id")).longValue();
            String defaultsJson = (String) row.get("defaults");
            Map<String, Object> defaults = defaultsJson != null && !defaultsJson.equals("null")
                ? parseJson(defaultsJson)
                : new HashMap<>();
            LocalDateTime updated = (LocalDateTime) row.get("last_updated");
            return new PlatformDefaults(id, defaults, updated);
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
     * Insert or update customer preferences for an account (upsert operation).
     * If preferences exist, update them; otherwise insert new row.
     * Preferences should be passed as a JSON string.
     *
     * @param accountId the account ID
     * @param preferencesJson the preferences as JSON string
     */
    @Insert("INSERT INTO customer_preferences (account_id, preferences, last_modified) " +
            "VALUES (#{accountId}, #{preferencesJson}::jsonb, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (account_id) DO UPDATE SET " +
            "preferences = #{preferencesJson}::jsonb, " +
            "last_modified = CURRENT_TIMESTAMP")
    void upsertPreferences(@Param("accountId") Long accountId, @Param("preferencesJson") String preferencesJson);

    /**
     * Delete customer preferences for an account, effectively resetting to platform defaults.
     *
     * @param accountId the account ID
     */
    @Delete("DELETE FROM customer_preferences WHERE account_id = #{accountId}")
    void deletePreferences(@Param("accountId") Long accountId);

    /**
     * Check if preferences exist for an account.
     *
     * @param accountId the account ID
     * @return true if preferences exist, false otherwise
     */
    @Select("SELECT EXISTS(SELECT 1 FROM customer_preferences WHERE account_id = #{accountId})")
    boolean existsByAccountId(@Param("accountId") Long accountId);
}
