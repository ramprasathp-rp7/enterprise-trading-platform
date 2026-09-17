package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PreferenceMapper;
import com.tradingplatform.config.PreferenceDefaults;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.exception.InvalidPreferenceException;
import com.tradingplatform.domain.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PreferenceService.
 * 
 * Tests cover:
 * - Nullable preferences handling
 * - Merge logic (Spring config defaults + account preferences)
 * - Partial updates
 * - Deletion (reset to NULL)
 * 
 * Note: Uses test stub implementations instead of mocks for AccountService and PreferenceDefaults
 * due to Mockito/ByteBuddy compatibility issues with Java 25.
 */
@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {
    @Mock
    private PreferenceMapper preferenceMapper;
    
    @Mock
    private AccountMapper accountMapper;
    
    @Mock
    private InstrumentMapper instrumentMapper;

    private PreferenceService preferenceService;
    private AccountService accountService;
    private PreferenceDefaults preferenceDefaults;
    private ObjectMapper objectMapper;
    private final Long testAccountId = 1L;
    
    /**
     * Stub implementation of PreferenceDefaults for testing
     */
    private static class PreferenceDefaultsStub extends PreferenceDefaults {
        private final Map<String, Object> configDefaults = new HashMap<>();
        
        public PreferenceDefaultsStub() {
            super();
            configDefaults.put("NOTIFICATION_EMAIL", true);
            configDefaults.put("NOTIFICATION_TRADING_ALERTS", true);
            configDefaults.put("DISPLAY_THEME", "light");
            configDefaults.put("DISPLAY_CURRENCY", "USD");
            configDefaults.put("TRADING_DEFAULT_ORDER_TYPE", "LIMIT");
            configDefaults.put("TRADING_MAX_ORDER_SIZE", 10000);
            configDefaults.put("TIMEZONE", "UTC");
            configDefaults.put("LANGUAGE", "en");
        }
        
        @Override
        public Map<String, Object> getDefaults() {
            return new HashMap<>(configDefaults);
        }
        
        @Override
        public Map<String, Object> getDefaultsCopy() {
            return new HashMap<>(configDefaults);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Mock accountMapper to return a valid account (avoid AccountNotFoundException)
        // Use lenient() to avoid UnnecessaryStubbingException for tests that don't use account service
        lenient().when(accountMapper.findByAccountId(testAccountId)).thenReturn(
            new Account(testAccountId, "ACC-001", "Test User", new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, 0)
        );
        
        accountService = new AccountService(accountMapper, instrumentMapper);
        preferenceDefaults = new PreferenceDefaultsStub();
        
        // Manually instantiate service with mocked preferences mapper and test stubs
        preferenceService = new PreferenceService(preferenceMapper, accountService, objectMapper, preferenceDefaults);
    }

    // ============ getPlatformDefaults Tests ============

    @Test
    void testGetPlatformDefaults_ReturnsConfigDefaults() {
        // Act
        Map<String, Object> result = preferenceService.getPlatformDefaults();

        // Assert
        assertNotNull(result);
        assertEquals(true, result.get("NOTIFICATION_EMAIL"));
        assertEquals("light", result.get("DISPLAY_THEME"));
        assertEquals("UTC", result.get("TIMEZONE"));
        assertEquals(8, result.size());
    }

    // ============ getPreferencesForAccount Tests ============

    @Test
    void testGetPreferencesForAccount_NullPreferences_ReturnsConfigDefaults() {
        // Setup: account exists, preferences are NULL (empty optional)
        when(preferenceMapper.getPreferencesForAccount(testAccountId)).thenReturn(Optional.empty());

        // Act
        Map<String, Object> result = preferenceService.getPreferencesForAccount(testAccountId);

        // Assert: should return all config defaults
        assertNotNull(result);
        assertEquals(true, result.get("NOTIFICATION_EMAIL"));
        assertEquals("light", result.get("DISPLAY_THEME"));
        assertEquals("UTC", result.get("TIMEZONE"));
        assertEquals(8, result.size());
        
        verify(preferenceMapper).getPreferencesForAccount(testAccountId);
    }

    @Test
    void testGetPreferencesForAccount_WithAccountPreferences_MergesWithDefaults() {
        // Setup: account has some preferences
        Map<String, Object> accountPrefs = new HashMap<>();
        accountPrefs.put("DISPLAY_THEME", "dark");  // Override default
        accountPrefs.put("LANGUAGE", "es");         // Override default
        
        when(preferenceMapper.getPreferencesForAccount(testAccountId)).thenReturn(Optional.of(accountPrefs));

        // Act
        Map<String, Object> result = preferenceService.getPreferencesForAccount(testAccountId);

        // Assert: config defaults + account overrides
        assertEquals("dark", result.get("DISPLAY_THEME"));    // Account override
        assertEquals("es", result.get("LANGUAGE"));           // Account override
        assertEquals(true, result.get("NOTIFICATION_EMAIL")); // Config default
        assertEquals("UTC", result.get("TIMEZONE"));          // Config default
        assertEquals(8, result.size());
    }

    @Test
    void testGetPreferencesForAccount_PartialAccountPreferences() {
        // Setup: account only overrides a couple of preferences
        Map<String, Object> accountPrefs = new HashMap<>();
        accountPrefs.put("DISPLAY_THEME", "dark");
        
        when(preferenceMapper.getPreferencesForAccount(testAccountId)).thenReturn(Optional.of(accountPrefs));

        // Act
        Map<String, Object> result = preferenceService.getPreferencesForAccount(testAccountId);

        // Assert: only one preference is overridden, rest are defaults
        assertEquals("dark", result.get("DISPLAY_THEME"));
        assertEquals("light", result.getOrDefault("DISPLAY_THEME_BACKUP", "light")); // Not overridden
        assertEquals("UTC", result.get("TIMEZONE"));                                  // Not overridden
        assertEquals(true, result.get("NOTIFICATION_EMAIL"));                         // Not overridden
    }

    // ============ updateAccountPreferences Tests ============

    @Test
    void testUpdateAccountPreferences_ValidPreferences_UpdatesSuccessfully() throws Exception {
        // Setup
        Map<String, Object> newPrefs = new HashMap<>();
        newPrefs.put("DISPLAY_THEME", "dark");
        
        Map<String, Object> existingPrefs = new HashMap<>();
        when(preferenceMapper.getPreferencesForAccount(testAccountId)).thenReturn(Optional.of(existingPrefs));

        // Act
        preferenceService.updateAccountPreferences(testAccountId, newPrefs);

        // Assert: mapper's updatePreferences should be called with merged preferences
        verify(preferenceMapper).updatePreferences(eq(testAccountId), contains("DISPLAY_THEME"));
    }

    @Test
    void testUpdateAccountPreferences_PartialUpdate_MergesWithExisting() throws Exception {
        // Setup: existing preferences with multiple keys
        Map<String, Object> existingPrefs = new HashMap<>();
        existingPrefs.put("DISPLAY_THEME", "light");
        existingPrefs.put("LANGUAGE", "en");
        
        when(preferenceMapper.getPreferencesForAccount(testAccountId)).thenReturn(Optional.of(existingPrefs));

        Map<String, Object> newPrefs = new HashMap<>();
        newPrefs.put("DISPLAY_THEME", "dark");  // Override

        // Act
        preferenceService.updateAccountPreferences(testAccountId, newPrefs);

        // Assert: both DISPLAY_THEME (updated) and LANGUAGE (retained) should be in the update
        verify(preferenceMapper).updatePreferences(argThat(accountId -> accountId.equals(testAccountId)), 
            argThat(json -> json.contains("DISPLAY_THEME") && json.contains("LANGUAGE")));
    }

    @Test
    void testUpdateAccountPreferences_NullPreferences_ThrowsException() {
        // Act & Assert
        assertThrows(InvalidPreferenceException.class, () ->
            preferenceService.updateAccountPreferences(testAccountId, null)
        );
    }

    @Test
    void testUpdateAccountPreferences_EmptyPreferences_ThrowsException() {
        // Act & Assert
        assertThrows(InvalidPreferenceException.class, () ->
            preferenceService.updateAccountPreferences(testAccountId, new HashMap<>())
        );
    }

    @Test
    void testUpdateAccountPreferences_NullKey_ThrowsException() {
        // Setup
        Map<String, Object> invalidPrefs = new HashMap<>();
        invalidPrefs.put(null, "value");

        // Act & Assert
        assertThrows(InvalidPreferenceException.class, () ->
            preferenceService.updateAccountPreferences(testAccountId, invalidPrefs)
        );
    }

    @Test
    void testUpdateAccountPreferences_BlankKey_ThrowsException() {
        // Setup
        Map<String, Object> invalidPrefs = new HashMap<>();
        invalidPrefs.put("", "value");

        // Act & Assert
        assertThrows(InvalidPreferenceException.class, () ->
            preferenceService.updateAccountPreferences(testAccountId, invalidPrefs)
        );
    }

    // ============ deleteAccountPreferences Tests ============

    @Test
    void testDeleteAccountPreferences_CallsResetPreferences() {
        // Act
        preferenceService.deleteAccountPreferences(testAccountId);

        // Assert: mapper's resetPreferences should be called with NULL
        verify(preferenceMapper).resetPreferences(testAccountId);
    }

    // ============ JSON Serialization Tests ============

    @Test
    void testJsonSerialization_MapToJsonString() throws Exception {
        // Test that preferences can be serialized to JSON for storage
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("DISPLAY_THEME", "dark");
        prefs.put("LANGUAGE", "en");
        prefs.put("NOTIFICATION_EMAIL", true);

        String json = objectMapper.writeValueAsString(prefs);

        assertNotNull(json);
        assertTrue(json.contains("DISPLAY_THEME"));
        assertTrue(json.contains("dark"));
    }

    @Test
    void testJsonDeserialization_JsonStringToMap() throws Exception {
        // Test that preferences can be deserialized from JSON for retrieval
        String json = "{\"DISPLAY_THEME\":\"dark\",\"LANGUAGE\":\"en\"}";

        Map<String, Object> prefs = objectMapper.readValue(json, Map.class);

        assertEquals("dark", prefs.get("DISPLAY_THEME"));
        assertEquals("en", prefs.get("LANGUAGE"));
    }
}
