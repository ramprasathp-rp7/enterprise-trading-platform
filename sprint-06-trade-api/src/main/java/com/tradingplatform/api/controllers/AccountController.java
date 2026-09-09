package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.domain.dto.*;
import com.tradingplatform.domain.enums.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
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

    public AccountController(AccountAccessAuthorizer accountAccessAuthorizer) {
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable long id, HttpServletRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(id, request);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable long id, HttpServletRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(id, request);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<PositionResponse> getPositions(@PathVariable long id, HttpServletRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(id, request);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
    
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable long id,
            HttpServletRequest request,
            @RequestParam(required = false) Optional<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> to) {
        accountAccessAuthorizer.assertTokenCanAccess(id, request);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
