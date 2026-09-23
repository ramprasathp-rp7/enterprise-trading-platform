package com.tradingplatform.domain.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Request DTO for updating customer preferences.
 * Accepts a map of preference key-value pairs to be persisted per account.
 * Null or missing keys are ignored; provided keys override existing values.
 */
public final class PreferenceRequest {
    private final Map<String, Object> preferences;

    public PreferenceRequest(Map<String, Object> preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreferenceRequest that = (PreferenceRequest) o;
        return Objects.equals(preferences, that.preferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preferences);
    }

    @Override
    public String toString() {
        return "PreferenceRequest{" +
                "preferences=" + preferences +
                '}';
    }
}
