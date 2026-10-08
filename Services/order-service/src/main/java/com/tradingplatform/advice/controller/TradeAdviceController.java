package com.tradingplatform.advice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.advice.dto.TradeAdviceResponse;
import com.tradingplatform.advice.service.TradeAdviceService;
import com.tradingplatform.api.security.AccountAccessAuthorizer;

/**
 * Trade Advice API: BUY / SELL / HOLD signals with reasons for the account's holdings and watchlists.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/advice")
public class TradeAdviceController {

    private final TradeAdviceService adviceService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public TradeAdviceController(TradeAdviceService adviceService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.adviceService = adviceService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    /**
     * GET /api/v1/accounts/{accountId}/advice
     * Signals for every held and watched instrument. 403 ACC-403 when the token is for another account.
     */
    @GetMapping
    public ResponseEntity<TradeAdviceResponse> getAdvice(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(adviceService.adviceFor(accountId));
    }
}
