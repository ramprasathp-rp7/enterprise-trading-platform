package com.tradingplatform.domain.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain model for customer preferences.
 * Represents account-level settings that override platform defaults.
 * Uses JSONB storage in PostgreSQL for flexible schema.
 */
public final class CustomerPreference {
    private final Long accountId;
    private final Map<String, Object> preferences;
    private final LocalDateTime lastModified;

    /**
     * Constructor for creating a CustomerPreference instance.
     *
     * @param accountId the account ID this preference belongs to
     * @param preferences a map of preference key-value pairs
     * @param lastModified the timestamp of last modification
     */
    public CustomerPreference(Long accountId, Map<String, Object> preferences, LocalDateTime lastModified) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
        this.lastModified = Objects.requireNonNull(lastModified, "lastModified cannot be null");
    }

    public Long getAccountId() {
        return accountId;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerPreference that = (CustomerPreference) o;
        return Objects.equals(accountId, that.accountId) &&
                Objects.equals(preferences, that.preferences) &&
                Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, preferences, lastModified);
    }

    @Override
    public String toString() {
        return "CustomerPreference{" +
                "accountId=" + accountId +
                ", preferences=" + preferences +
                ", lastModified=" + lastModified +
                '}';
    }
}
