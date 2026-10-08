package com.tradingplatform.watchlist.alerts.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.watchlist.alerts.dto.CreatePriceAlertRequest;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertResponse;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertTriggerResponse;
import com.tradingplatform.watchlist.alerts.dto.UpdatePriceAlertRequest;
import com.tradingplatform.watchlist.alerts.service.PriceAlertService;

import jakarta.validation.Valid;

/**
 * Price alert endpoints (contract: watchlist-api.yaml, tag PriceAlerts).
 * - GET    /api/v1/accounts/{accountId}/price-alerts
 * - POST   /api/v1/accounts/{accountId}/price-alerts
 * - GET    /api/v1/accounts/{accountId}/price-alerts/triggers
 * - GET    /api/v1/accounts/{accountId}/price-alerts/{alertId}
 * - PATCH  /api/v1/accounts/{accountId}/price-alerts/{alertId}
 * - DELETE /api/v1/accounts/{accountId}/price-alerts/{alertId}
 * - GET    /api/v1/accounts/{accountId}/price-alerts/{alertId}/triggers
 *
 * Every endpoint checks that the token's accountId claim matches the path (ACC-403).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/price-alerts")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public PriceAlertController(PriceAlertService priceAlertService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.priceAlertService = priceAlertService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @GetMapping
    public ResponseEntity<List<PriceAlertResponse>> getPriceAlerts(
            @PathVariable long accountId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String symbol) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(priceAlertService.getAlerts(accountId, active, symbol));
    }

    @PostMapping
    public ResponseEntity<PriceAlertResponse> createPriceAlert(
            @PathVariable long accountId,
            @Valid @RequestBody CreatePriceAlertRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(priceAlertService.createAlert(accountId, request));
    }

    @GetMapping("/triggers")
    public ResponseEntity<List<PriceAlertTriggerResponse>> getRecentPriceAlertTriggers(
            @PathVariable long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(priceAlertService.getRecentTriggers(accountId, since));
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<PriceAlertResponse> getPriceAlert(@PathVariable long accountId, @PathVariable long alertId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(priceAlertService.getAlert(accountId, alertId));
    }

    @PatchMapping("/{alertId}")
    public ResponseEntity<PriceAlertResponse> updatePriceAlert(
            @PathVariable long accountId,
            @PathVariable long alertId,
            @Valid @RequestBody UpdatePriceAlertRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(priceAlertService.setActive(accountId, alertId, request.active()));
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> deletePriceAlert(@PathVariable long accountId, @PathVariable long alertId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        priceAlertService.deleteAlert(accountId, alertId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{alertId}/triggers")
    public ResponseEntity<List<PriceAlertTriggerResponse>> getPriceAlertTriggers(
            @PathVariable long accountId,
            @PathVariable long alertId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(priceAlertService.getTriggers(accountId, alertId));
    }
}
