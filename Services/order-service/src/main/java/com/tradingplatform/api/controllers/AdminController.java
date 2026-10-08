package com.tradingplatform.api.controllers;

import com.tradingplatform.api.services.AccountHistoryScheduler;
import com.tradingplatform.api.services.OrderHistoryService;
import com.tradingplatform.api.services.TransactionService;
import com.tradingplatform.api.services.FeeService;
import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import com.tradingplatform.domain.dto.FeeStructureResponse;
import com.tradingplatform.domain.dto.FeeUpdateRequest;
import com.tradingplatform.domain.dto.AccountHistorySnapshotResponse;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Audit & Compliance Controller
 * Centralized endpoints for system-wide administration, auditing, and compliance.
 * Requires admin authorization.
 * 
 * Provides:
 * - Complete order history and transaction records across all accounts
 * - Asset fee structure management
 * - Compliance reporting and auditing
 * - On-demand account history snapshots
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    
    private final OrderHistoryService orderHistoryService;
    private final TransactionService transactionService;
    private final FeeService feeService;
    
    private final AccountHistoryScheduler accountHistoryScheduler;

    public AdminController(OrderHistoryService orderHistoryService,
                          TransactionService transactionService,
                          FeeService feeService,
                          AccountHistoryScheduler accountHistoryScheduler) {
        this.orderHistoryService = orderHistoryService;
        this.transactionService = transactionService;
        this.feeService = feeService;
        this.accountHistoryScheduler = accountHistoryScheduler;
    }
    
    /**
     * Retrieve complete order history across all accounts (admin only).
     * GET /api/v1/admin/orders/history
     * 
     * Supports optional date range filtering for time-bounded audit queries.
     * Returns all order status changes (creation → transitions → final state) 
     * sorted newest-first.
     *
     * @param from optional start date (inclusive, ISO 8601 format)
     * @param to optional end date (inclusive, ISO 8601 format)
     * @return list of order status changes across all accounts
     */
    @GetMapping("/orders/history")
    public ResponseEntity<List<OrderStatusChange>> getCompleteOrderHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> to) {
        
        // Convert OffsetDateTime to Instant
        Optional<Instant> fromInstant = from.map(OffsetDateTime::toInstant);
        Optional<Instant> toInstant = to.map(OffsetDateTime::toInstant);
        
        List<OrderStatusChange> history;
        
        if (fromInstant.isPresent() && toInstant.isPresent()) {
            history = orderHistoryService.getAllOrderHistory(fromInstant.get(), toInstant.get());
        } else {
            history = orderHistoryService.getAllOrderHistory();
        }
        
        return ResponseEntity.ok(history);
    }
    
    /**
     * Retrieve complete transaction history across all accounts (admin only).
     * GET /api/v1/admin/transactions
     * 
     * Supports optional date range filtering for time-bounded compliance queries.
     * Returns all transactions (deposits, withdrawals, order settlements) 
     * sorted newest-first.
     *
     * @param from optional start date (inclusive, ISO 8601 format)
     * @param to optional end date (inclusive, ISO 8601 format)
     * @return list of all transactions across all accounts
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionHistoryEntry>> getCompleteTransactionHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<OffsetDateTime> to) {
        
        // Convert OffsetDateTime to Instant
        Optional<Instant> fromInstant = from.map(OffsetDateTime::toInstant);
        Optional<Instant> toInstant = to.map(OffsetDateTime::toInstant);
        
        List<TransactionHistoryEntry> history;
        
        if (fromInstant.isPresent() && toInstant.isPresent()) {
            history = transactionService.getAllTransactions(fromInstant.get(), toInstant.get());
        } else {
            history = transactionService.getAllTransactions();
        }
        
        return ResponseEntity.ok(history);
    }
    
    /**
     * Retrieve all asset fee structures (admin only).
     * GET /api/v1/admin/fees
     * 
     * Returns fee configuration for all asset classes.
     *
     * @return list of all fee structures
     */
    @GetMapping("/fees")
    public ResponseEntity<List<FeeStructureResponse>> getAllFees() {
        List<FeeStructureResponse> fees = feeService.getAllFees();
        return ResponseEntity.ok(fees);
    }
    
    /**
     * Retrieve fee structure for a specific asset type (admin only).
     * GET /api/v1/admin/fees/{assetType}
     * 
     * @param assetType the asset type (e.g., "EQUITY", "FOREX")
     * @return the fee structure for the asset type
     */
    @GetMapping("/fees/{assetType}")
    public ResponseEntity<FeeStructureResponse> getFeeByAssetType(@PathVariable String assetType) {
        FeeStructureResponse fee = feeService.getFeeByAssetType(assetType);
        return ResponseEntity.ok(fee);
    }

    /**
     * Update the fee structure for a specific asset type (admin only).
     * PUT /api/v1/admin/fees/{assetType}
     *
     * Takes effect for every order placed after the update; orders already placed keep the fee
     * they were quoted.
     *
     * @param assetType the asset type (e.g., "EQUITY", "FX")
     * @param request the new flat fee and percentage fee (decimal form, 0.0001 = 0.01%)
     * @return the updated fee structure
     */
    @PutMapping("/fees/{assetType}")
    public ResponseEntity<FeeStructureResponse> updateFee(@PathVariable String assetType,
                                                          @Valid @RequestBody FeeUpdateRequest request) {
        FeeStructureResponse fee = feeService.updateFee(assetType, request);
        return ResponseEntity.ok(fee);
    }

    /**
     * Capture an account history snapshot for every active account right now (admin only).
     * POST /api/v1/admin/account-history/snapshot
     *
     * Runs the same logic as the nightly 23:59 UTC job. Rows are stamped with today's
     * 23:59:59 UTC, so running it again (or the nightly job running later) overwrites them.
     *
     * @return snapshot date and how many accounts succeeded/failed
     */
    @PostMapping("/account-history/snapshot")
    public ResponseEntity<AccountHistorySnapshotResponse> captureAccountHistorySnapshot() {
        AccountHistorySnapshotResponse result = accountHistoryScheduler.triggerManualSnapshot();
        return ResponseEntity.ok(result);
    }
}
