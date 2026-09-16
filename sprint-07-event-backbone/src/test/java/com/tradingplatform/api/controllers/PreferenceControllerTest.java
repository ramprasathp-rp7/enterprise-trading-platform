package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.IPreferenceService;
import com.tradingplatform.domain.dto.PreferenceRequest;
import com.tradingplatform.domain.exception.InvalidPreferenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreferenceControllerTest {

    private PreferenceController controller;
    private FakePreferenceService preferenceService;
    private FakeAccountAccessAuthorizer authorizer;

    private static final Long TEST_ACCOUNT_ID = 123L;

    @BeforeEach
    void setUp() {
        preferenceService = new FakePreferenceService();
        authorizer = new FakeAccountAccessAuthorizer();

        controller = new PreferenceController(
                preferenceService,
                authorizer
        );
    }

    @Test
    void testGetPreferences_Success() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("DISPLAY_THEME", "dark");
        preferences.put("NOTIFICATION_EMAIL", true);
        preferences.put("TIMEZONE", "UTC");

        preferenceService.preferences = preferences;

        ResponseEntity<?> response =
                controller.getPreferences(TEST_ACCOUNT_ID);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(TEST_ACCOUNT_ID, authorizer.lastAccountId);
        assertEquals(1, preferenceService.getPreferencesCallCount);
    }

    @Test
    void testGetPreferences_Unauthorized() {
        authorizer.shouldThrow = true;

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.getPreferences(TEST_ACCOUNT_ID)
        );

        assertEquals(
                0,
                preferenceService.getPreferencesCallCount
        );
    }

    @Test
    void testUpdatePreferences_Success() {
        Map<String, Object> newPreferences = new HashMap<>();
        newPreferences.put("DISPLAY_THEME", "dark");

        Map<String, Object> updatedPreferences = new HashMap<>();
        updatedPreferences.put("DISPLAY_THEME", "dark");
        updatedPreferences.put("NOTIFICATION_EMAIL", true);
        updatedPreferences.put("TIMEZONE", "UTC");

        preferenceService.preferences = updatedPreferences;

        PreferenceRequest request =
                new PreferenceRequest(newPreferences);

        ResponseEntity<?> response =
                controller.updatePreferences(
                        TEST_ACCOUNT_ID,
                        request
                );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(TEST_ACCOUNT_ID, authorizer.lastAccountId);
        assertEquals(
                TEST_ACCOUNT_ID,
                preferenceService.lastUpdatedAccountId
        );
        assertEquals(
                newPreferences,
                preferenceService.lastUpdatedPreferences
        );
    }

    @Test
    void testUpdatePreferences_InvalidPreferences() {
        Map<String, Object> invalidPreferences = new HashMap<>();
        invalidPreferences.put("invalidKey", "invalidValue");

        preferenceService.shouldThrowInvalidPreference = true;

        PreferenceRequest request =
                new PreferenceRequest(invalidPreferences);

        assertThrows(
                InvalidPreferenceException.class,
                () -> controller.updatePreferences(
                        TEST_ACCOUNT_ID,
                        request
                )
        );

        assertEquals(
                invalidPreferences,
                preferenceService.lastUpdatedPreferences
        );
    }

    @Test
    void testUpdatePreferences_Unauthorized() {
        authorizer.shouldThrow = true;

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("DISPLAY_THEME", "dark");

        PreferenceRequest request =
                new PreferenceRequest(preferences);

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.updatePreferences(
                        TEST_ACCOUNT_ID,
                        request
                )
        );

        assertEquals(
                0,
                preferenceService.updateCallCount
        );
    }

    @Test
    void testDeletePreferences_Success() {
        ResponseEntity<?> response =
                controller.deletePreferences(TEST_ACCOUNT_ID);

        assertEquals(204, response.getStatusCode().value());

        assertEquals(
                TEST_ACCOUNT_ID,
                preferenceService.lastDeletedAccountId
        );
    }

    @Test
    void testDeletePreferences_Unauthorized() {
        authorizer.shouldThrow = true;

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.deletePreferences(TEST_ACCOUNT_ID)
        );

        assertEquals(
                null,
                preferenceService.lastDeletedAccountId
        );
    }

    @Test
    void testGetPlatformDefaults_Success() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("NOTIFICATION_EMAIL", true);
        defaults.put("DISPLAY_THEME", "light");
        defaults.put("TIMEZONE", "UTC");
        defaults.put("LANGUAGE", "en");

        preferenceService.defaults = defaults;

        ResponseEntity<?> response =
                controller.getPlatformDefaults();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(1, preferenceService.getDefaultsCallCount);
    }


    // ---------------------------------------------------------
    // Fake AccountAccessAuthorizer
    // ---------------------------------------------------------

    private static class FakeAccountAccessAuthorizer
            extends AccountAccessAuthorizer {

        boolean shouldThrow = false;
        Long lastAccountId = null;

        @Override
        public void assertTokenCanAccess(long requestedAccountId) {

            lastAccountId = requestedAccountId;

            if (shouldThrow) {
                throw new IllegalArgumentException("Unauthorized");
            }
        }
    }


    // ---------------------------------------------------------
    // Fake Preference Service
    // ---------------------------------------------------------

    private static class FakePreferenceService
            implements IPreferenceService {

        Map<String, Object> preferences = new HashMap<>();
        Map<String, Object> defaults = new HashMap<>();

        Long lastUpdatedAccountId;
        Long lastDeletedAccountId;

        Map<String, Object> lastUpdatedPreferences;

        int getPreferencesCallCount = 0;
        int updateCallCount = 0;
        int getDefaultsCallCount = 0;

        boolean shouldThrowInvalidPreference = false;


        @Override
        public Map<String, Object> getPreferencesForAccount(
                Long accountId) {

            getPreferencesCallCount++;

            return preferences;
        }


        @Override
        public void updateAccountPreferences(
                Long accountId,
                Map<String, Object> preferences) {

            updateCallCount++;

            lastUpdatedAccountId = accountId;
            lastUpdatedPreferences = preferences;

            if (shouldThrowInvalidPreference) {
                throw new InvalidPreferenceException(
                        "Preference validation failed"
                );
            }
        }


        @Override
        public void deleteAccountPreferences(Long accountId) {

            lastDeletedAccountId = accountId;
        }


        @Override
        public Map<String, Object> getPlatformDefaults() {

            getDefaultsCallCount++;

            return defaults;
        }
    }
}