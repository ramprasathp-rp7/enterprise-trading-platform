package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Health check response for the Portfolio P&L service.
 * Reports service status and dependency status (postgres, fauxnance, etc).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class HealthResponse {
    
    @JsonProperty("status")
    private final String status;
    
    @JsonProperty("dependencies")
    private final List<DependencyStatus> dependencies;
    
    @JsonProperty("asOf")
    private final Instant asOf;

    public HealthResponse(String status, List<DependencyStatus> dependencies, Instant asOf) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies cannot be null");
        this.asOf = Objects.requireNonNull(asOf, "asOf cannot be null");
    }

    public String getStatus() {
        return status;
    }

    public List<DependencyStatus> getDependencies() {
        return dependencies;
    }

    public Instant getAsOf() {
        return asOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthResponse that = (HealthResponse) o;
        return Objects.equals(status, that.status) &&
                Objects.equals(dependencies, that.dependencies) &&
                Objects.equals(asOf, that.asOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, dependencies, asOf);
    }

    @Override
    public String toString() {
        return "HealthResponse{" +
                "status='" + status + '\'' +
                ", dependencies=" + dependencies +
                ", asOf=" + asOf +
                '}';
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class DependencyStatus {
        
        @JsonProperty("name")
        private final String name;
        
        @JsonProperty("status")
        private final String status;
        
        @JsonProperty("quotaRemaining")
        private final Integer quotaRemaining;

        public DependencyStatus(String name, String status) {
            this(name, status, null);
        }

        public DependencyStatus(String name, String status, Integer quotaRemaining) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.status = Objects.requireNonNull(status, "status cannot be null");
            this.quotaRemaining = quotaRemaining;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }

        public Integer getQuotaRemaining() {
            return quotaRemaining;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DependencyStatus that = (DependencyStatus) o;
            return Objects.equals(name, that.name) &&
                    Objects.equals(status, that.status) &&
                    Objects.equals(quotaRemaining, that.quotaRemaining);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, status, quotaRemaining);
        }

        @Override
        public String toString() {
            return "DependencyStatus{" +
                    "name='" + name + '\'' +
                    ", status='" + status + '\'' +
                    ", quotaRemaining=" + quotaRemaining +
                    '}';
        }
    }
}
