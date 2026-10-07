package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.PortfolioPnlService;
import com.tradingplatform.domain.dto.PnlResponse;
import com.tradingplatform.domain.dto.PortfolioSummary;
import com.tradingplatform.domain.dto.PricedPosition;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Portfolio and P&L REST API endpoints.
 * Exposes priced holdings, portfolio summary, and P&L calculations.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
    
    private final PortfolioPnlService portfolioPnlService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public PortfolioController(PortfolioPnlService portfolioPnlService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.portfolioPnlService = portfolioPnlService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    /**
     * GET /api/v1/portfolio/{accountId}
     * Returns the portfolio summary for an account: cash, market value, cost basis, P&L totals.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<PortfolioSummary> getPortfolioSummary(@PathVariable Long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        PortfolioSummary summary = portfolioPnlService.getPortfolioSummary(accountId);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/v1/portfolio/{accountId}/positions
     * Returns all open positions for an account with pricing and unrealised P&L.
     * Optional query parameter: symbol - filter to a specific instrument.
     */
    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<PricedPosition>> getPricedPositions(
            @PathVariable Long accountId,
            @RequestParam(required = false) String symbol) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        List<PricedPosition> positions = portfolioPnlService.getPricedPositions(accountId, symbol);
        return ResponseEntity.ok(positions);
    }

    /**
     * GET /api/v1/portfolio/{accountId}/pnl
     * Returns realised and unrealised P&L for an account.
     * Optional query parameters:
     *   - from: LocalDate - lower bound for realised P&L (inclusive)
     *   - to: LocalDate - upper bound for realised P&L (inclusive)
     *   - bySymbol: boolean - include per-instrument breakdown (default: false)
     */
    @GetMapping("/{accountId}/pnl")
    public ResponseEntity<PnlResponse> getPnl(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean bySymbol) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        PnlResponse pnl = portfolioPnlService.getPnl(accountId, from, to, bySymbol);
        return ResponseEntity.ok(pnl);
    }
}
