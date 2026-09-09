package com.tradingplatform.api.controllers;

import com.tradingplatform.domain.dto.*;
import com.tradingplatform.domain.enums.OrderStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
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
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String id) {
        return new ResponseEntity<>;
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String id) {
        return new ResponseEntity<>;
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<PositionResponse> getPositions(@PathVariable String id) {
        return new ResponseEntity<>;
    }
    
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable String id,
            @RequestParam(required = false) Optional<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<Instant> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<Instant> to) {
                return new ResponseEntity<>();
            }
       
