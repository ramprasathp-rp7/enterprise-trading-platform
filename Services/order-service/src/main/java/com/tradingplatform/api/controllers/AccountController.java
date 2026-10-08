package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.api.services.AccountHistoryService;
import com.tradingplatform.domain.dto.*;
import com.tradingplatform.domain.enums.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController 
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final AccountService accountService;
    private final AccountHistoryService accountHistoryService;

    public AccountController(AccountAccessAuthorizer accountAccessAuthorizer, AccountService accountService, AccountHistoryService accountHistoryService) {
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.accountService = accountService;
        this.accountHistoryService = accountHistoryService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable long id) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        AccountResponse response = accountService.getAccount(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable long id) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        BalanceResponse response = accountService.getBalance(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(@PathVariable long id) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        List<PositionResponse> response = accountService.getPositions(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable long id,
            @RequestParam(required = false) Optional<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> to) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        // Convert OffsetDateTime parameters to Instant for service layer
        Optional<Instant> fromInstant = from.map(OffsetDateTime::toInstant);
        Optional<Instant> toInstant = to.map(OffsetDateTime::toInstant);
        List<OrderHistoryEntry> response = accountService.getOrders(id, status, fromInstant, toInstant);
        return ResponseEntity.ok(response);
    }

    // Get account history snapshots (most recent first, 30 by default, max 365).
    @GetMapping("/{id}/history")
    public ResponseEntity<List<AccountHistoryResponse>> getAccountHistory(
            @PathVariable long id,
            @RequestParam(defaultValue = "30") int limit) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        List<AccountHistoryResponse> response = accountHistoryService.getAccountHistory(id, limit);
        return ResponseEntity.ok(response);
    }

    // Get account history snapshots within a date range (both dates inclusive, UTC).
    @GetMapping("/{id}/history/range")
    public ResponseEntity<List<AccountHistoryResponse>> getAccountHistoryRange(
            @PathVariable long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<AccountHistoryResponse> response = accountHistoryService.getAccountHistoryRange(id, fromInstant, toInstant);
        return ResponseEntity.ok(response);
    }
}
