package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.TransactionService;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import com.tradingplatform.domain.dto.TransactionRequest;
import com.tradingplatform.domain.dto.TransactionResponse;
import com.tradingplatform.domain.enums.TransactionType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for transaction operations.
 * Handles deposits, withdrawals, and transaction history queries.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class TransactionController {

    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final TransactionService transactionService;

    public TransactionController(AccountAccessAuthorizer accountAccessAuthorizer,
                                TransactionService transactionService) {
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.transactionService = transactionService;
    }

    /**
     * Deposit funds into an account.
     * POST /api/v1/accounts/{id}/deposits
     *
     * @param id the account ID
     * @param request the deposit request containing amount
     * @return the created transaction response
     */
    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable long id,
            @Valid @RequestBody TransactionRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        TransactionResponse response = transactionService.deposit(id, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Withdraw funds from an account.
     * POST /api/v1/accounts/{id}/withdrawals
     *
     * @param id the account ID
     * @param request the withdrawal request containing amount
     * @return the created transaction response
     */
    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable long id,
            @Valid @RequestBody TransactionRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        TransactionResponse response = transactionService.withdraw(id, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve a specific transaction by ID.
     * GET /api/v1/accounts/{id}/transactions/{transactionId}
     *
     * @param id the account ID
     * @param transactionId the transaction ID
     * @return the transaction details
     */
    @GetMapping("/{id}/transactions/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable long id,
            @PathVariable UUID transactionId) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        TransactionResponse response = transactionService.getTransaction(transactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve transaction history for an account.
     * Supports optional filtering by date range and transaction type.
     * GET /api/v1/accounts/{id}/transactions
     *
     * @param id the account ID
     * @param from optional start date (inclusive, ISO 8601 format)
     * @param to optional end date (inclusive, ISO 8601 format)
     * @param type optional transaction type filter
     * @return list of transaction history entries (most recent first)
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionHistoryEntry>> getTransactionHistory(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> to,
            @RequestParam(required = false) Optional<TransactionType> type) {
        accountAccessAuthorizer.assertTokenCanAccess(id);
        
        // Convert OffsetDateTime to Instant
        Optional<Instant> fromInstant = from.map(OffsetDateTime::toInstant);
        Optional<Instant> toInstant = to.map(OffsetDateTime::toInstant);
        
        List<TransactionHistoryEntry> response = transactionService.getTransactionHistory(
            id, fromInstant, toInstant, type);
        return ResponseEntity.ok(response);
    }
}
