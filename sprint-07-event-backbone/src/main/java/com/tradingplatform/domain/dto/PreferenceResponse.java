package com.tradingplatform.domain.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Response DTO for customer preferences.
 * Returns merged preferences (account-level preferences override platform defaults).
 * Includes metadata about when preferences were last modified.
 */
public final class PreferenceResponse {
    private final Long accountId;
    private final Map<String, Object> preferences;
    private final LocalDateTime lastModified;

    public PreferenceResponse(Long accountId, Map<String, Object> preferences, LocalDateTime lastModified) {
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
        PreferenceResponse that = (PreferenceResponse) o;
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
        return "PreferenceResponse{" +
                "accountId=" + accountId +
                ", preferences=" + preferences +
                ", lastModified=" + lastModified +
                '}';
    }
}
