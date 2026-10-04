package com.tradingplatform.domain.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Response DTO for platform-wide default preferences.
 * Returns the system-wide default preferences that can be overridden at account level.
 */
public final class PlatformDefaultsResponse {
    private final Map<String, Object> defaults;

    public PlatformDefaultsResponse(Map<String, Object> defaults) {
        this.defaults = Objects.requireNonNull(defaults, "defaults cannot be null");
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformDefaultsResponse that = (PlatformDefaultsResponse) o;
        return Objects.equals(defaults, that.defaults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaults);
    }

    @Override
    public String toString() {
        return "PlatformDefaultsResponse{" +
                "defaults=" + defaults +
                '}';
    }
}
