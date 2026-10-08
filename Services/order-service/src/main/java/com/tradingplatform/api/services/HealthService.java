package com.tradingplatform.api.services;

import com.tradingplatform.domain.dto.HealthResponse;
import com.tradingplatform.domain.dto.HealthResponse.DependencyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for monitoring health and dependency status.
 * Checks: database connectivity and Fauxnance API quota.
 */
@Service
public class HealthService {
    
    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Optional<FauxnanceApiClient> fauxnanceApiClient;

    public HealthService(JdbcTemplate jdbcTemplate, 
                        @Autowired(required = false) FauxnanceApiClient fauxnanceApiClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.fauxnanceApiClient = Optional.ofNullable(fauxnanceApiClient);
    }

    /**
     * Get overall service health and dependency status.
     * Service is always "ok" if this endpoint responds; dependencies may be degraded.
     */
    public HealthResponse getHealth() {
        Instant now = Instant.now();
        List<DependencyStatus> dependencies = new ArrayList<>();

        // Check Postgres
        DependencyStatus postgresStatus = checkPostgres();
        dependencies.add(postgresStatus);

        // Check Fauxnance API (if client is available)
        if (fauxnanceApiClient.isPresent()) {
            DependencyStatus fauxnanceStatus = checkFauxnance();
            dependencies.add(fauxnanceStatus);
        }

        // Determine overall status: "ok" if all dependencies ok, "degraded" otherwise
        String overallStatus = dependencies.stream()
                .allMatch(d -> "ok".equals(d.getStatus()))
                ? "ok"
                : "degraded";

        return new HealthResponse(overallStatus, dependencies, now);
    }

    /**
     * Check Postgres connectivity with a simple query.
     */
    private DependencyStatus checkPostgres() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.debug("Postgres health check passed");
            return new DependencyStatus("postgres", "ok", null);
        } catch (DataAccessException ex) {
            log.warn("Postgres health check failed: {}", ex.getMessage());
            return new DependencyStatus("postgres", "degraded", null);
        }
    }

    /**
     * Check Fauxnance API by retrieving quota information.
     */
    private DependencyStatus checkFauxnance() {
        try {
            int quotaRemaining = fauxnanceApiClient.get().getQuotaRemaining();
            log.debug("Fauxnance quota check passed: {} remaining", quotaRemaining);
            return new DependencyStatus("fauxnance", "ok", quotaRemaining);
        } catch (Exception ex) {
            log.warn("Fauxnance health check failed: {}", ex.getMessage());
            return new DependencyStatus("fauxnance", "degraded", null);
        }
    }
}
