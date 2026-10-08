package com.tradingplatform.strategy.controller;

import java.util.List;

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
import com.tradingplatform.strategy.dto.CreateStrategyRequest;
import com.tradingplatform.strategy.dto.StrategyExecutionResponse;
import com.tradingplatform.strategy.dto.StrategyResponse;
import com.tradingplatform.strategy.dto.UpdateStrategyRequest;
import com.tradingplatform.strategy.service.StrategyService;

import jakarta.validation.Valid;

/**
 * Automated strategy endpoints (contract: strategy-api.yaml).
 * - GET    /api/v1/accounts/{accountId}/strategies
 * - POST   /api/v1/accounts/{accountId}/strategies
 * - GET    /api/v1/accounts/{accountId}/strategies/{strategyId}
 * - PATCH  /api/v1/accounts/{accountId}/strategies/{strategyId}
 * - DELETE /api/v1/accounts/{accountId}/strategies/{strategyId}
 * - GET    /api/v1/accounts/{accountId}/strategies/{strategyId}/executions
 *
 * Every endpoint checks that the token's accountId claim matches the path (ACC-403).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/strategies")
public class StrategyController {

    private final StrategyService strategyService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public StrategyController(StrategyService strategyService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.strategyService = strategyService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @GetMapping
    public ResponseEntity<List<StrategyResponse>> getStrategies(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(strategyService.getStrategies(accountId));
    }

    @PostMapping
    public ResponseEntity<StrategyResponse> createStrategy(@PathVariable long accountId,
                                                           @Valid @RequestBody CreateStrategyRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(strategyService.createStrategy(accountId, request));
    }

    @GetMapping("/{strategyId}")
    public ResponseEntity<StrategyResponse> getStrategy(@PathVariable long accountId, @PathVariable long strategyId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(strategyService.getStrategy(accountId, strategyId));
    }

    @PatchMapping("/{strategyId}")
    public ResponseEntity<StrategyResponse> updateStrategy(@PathVariable long accountId, @PathVariable long strategyId,
                                                           @Valid @RequestBody UpdateStrategyRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(strategyService.setActive(accountId, strategyId, request.active()));
    }

    @DeleteMapping("/{strategyId}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable long accountId, @PathVariable long strategyId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        strategyService.deleteStrategy(accountId, strategyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{strategyId}/executions")
    public ResponseEntity<List<StrategyExecutionResponse>> getExecutions(@PathVariable long accountId,
                                                                         @PathVariable long strategyId,
                                                                         @RequestParam(required = false) Integer limit) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(strategyService.getExecutions(accountId, strategyId, limit));
    }
}
