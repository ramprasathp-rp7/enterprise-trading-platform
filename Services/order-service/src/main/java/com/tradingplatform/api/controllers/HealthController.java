package com.tradingplatform.api.controllers;

import com.tradingplatform.api.services.HealthService;
import com.tradingplatform.domain.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint for the Portfolio and P&L service.
 * No authentication required; reports service and dependency status.
 */
@RestController
@RequestMapping("/health")
public class HealthController {
    
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * GET /health
     * Returns service health status and dependency information.
     * Service responds with 200 regardless of dependency status;
     * dependencies may show degraded if unavailable.
     */
    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse health = healthService.getHealth();
        return ResponseEntity.ok(health);
    }
}
