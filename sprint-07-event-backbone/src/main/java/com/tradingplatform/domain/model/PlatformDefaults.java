package com.tradingplatform.domain.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain model for platform-wide default preferences.
 * Single instance (id=1) enforced at database level.
 * Account preferences override these defaults when merged.
 */
public final class PlatformDefaults {
    private final Long id;
    private final Map<String, Object> defaults;
    private final LocalDateTime lastUpdated;

    /**
     * Constructor for creating PlatformDefaults instance.
     *
     * @param id the ID (should always be 1)
     * @param defaults a map of default preference key-value pairs
     * @param lastUpdated the timestamp of last update
     */
    public PlatformDefaults(Long id, Map<String, Object> defaults, LocalDateTime lastUpdated) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.defaults = Objects.requireNonNull(defaults, "defaults cannot be null");
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
    }

    public Long getId() {
        return id;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformDefaults that = (PlatformDefaults) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(defaults, that.defaults) &&
                Objects.equals(lastUpdated, that.lastUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, defaults, lastUpdated);
    }

    @Override
    public String toString() {
        return "PlatformDefaults{" +
                "id=" + id +
                ", defaults=" + defaults +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
