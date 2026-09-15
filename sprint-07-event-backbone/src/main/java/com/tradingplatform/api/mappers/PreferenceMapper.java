package com.tradingplatform.api.mappers;

import com.tradingplatform.domain.model.CustomerPreference;
import com.tradingplatform.domain.model.PlatformDefaults;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
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
     * Retrieve customer preferences for a given account.
     * Preferences are stored and retrieved as JSON in the database.
     *
     * @param accountId the account ID
     * @return Optional containing CustomerPreference if found, empty otherwise
     */
    @Select("SELECT account_id, preferences, last_modified FROM customer_preferences WHERE account_id = #{accountId}")
    @ConstructorArgs({
            @Arg(column = "account_id", javaType = Long.class),
            @Arg(column = "preferences", javaType = Map.class),
            @Arg(column = "last_modified", javaType = LocalDateTime.class)
    })
    Optional<CustomerPreference> findPreferencesByAccountId(@Param("accountId") Long accountId);

    /**
     * Retrieve platform-wide default preferences.
     * Defaults are stored and retrieved as JSON in the database.
     *
     * @return Optional containing PlatformDefaults if found, empty otherwise
     */
    @Select("SELECT id, defaults, last_updated FROM platform_defaults WHERE id = 1")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "defaults", javaType = Map.class),
            @Arg(column = "last_updated", javaType = LocalDateTime.class)
    })
    Optional<PlatformDefaults> findPlatformDefaults();

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
