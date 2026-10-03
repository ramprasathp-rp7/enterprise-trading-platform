package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.FeeService;
import com.tradingplatform.domain.dto.FeeStructureResponse;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Fee Management Controller
 * Endpoints for managing asset class fee structures.
 * Requires admin authorization.
 */
@RestController
@RequestMapping("/api/v1/admin/fees")
public class FeeController {
    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    /**
     * GET /api/v1/admin/fees
     * Get all asset fee structures (admin only).
     * Admin users can access this endpoint regardless of their account ID.
     */
    @GetMapping
    public ResponseEntity<List<FeeStructureResponse>> getAllFees() {
        List<FeeStructureResponse> fees = feeService.getAllFees();
        return ResponseEntity.ok(fees);
    }

    /**
     * GET /api/v1/admin/fees/{assetType}
     * Get fee structure for a specific asset type (admin only).
     */
    @GetMapping("/{assetType}")
    public ResponseEntity<FeeStructureResponse> getFeeByAssetType(@PathVariable String assetType) {
        FeeStructureResponse fee = feeService.getFeeByAssetType(assetType);
        return ResponseEntity.ok(fee);
    }
}
