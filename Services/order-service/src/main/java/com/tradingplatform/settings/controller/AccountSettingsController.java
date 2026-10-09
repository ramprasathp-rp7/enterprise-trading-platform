package com.tradingplatform.settings.controller;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.settings.model.AccountSettings;
import com.tradingplatform.settings.service.AccountSettingsService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The signed-in customer's trading settings (contract: account-settings-api.yaml).
 * Every route compares the token's accountId with the path first (ACC-403).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/settings")
public class AccountSettingsController {

    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final AccountSettingsService settingsService;

    public AccountSettingsController(AccountAccessAuthorizer accountAccessAuthorizer, AccountSettingsService settingsService) {
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<AccountSettingsResponse> getAccountSettings(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(AccountSettingsResponse.from(settingsService.get(accountId)));
    }

    @PutMapping
    public ResponseEntity<AccountSettingsResponse> updateAccountSettings(@PathVariable long accountId,
                                                                         @Valid @RequestBody AccountSettingsRequest request) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        return ResponseEntity.ok(AccountSettingsResponse.from(
            settingsService.replace(accountId, request.orderBudget(), request.market(), request.defaultQuantity())));
    }

    @DeleteMapping
    public ResponseEntity<Void> resetAccountSettings(@PathVariable long accountId) {
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        settingsService.reset(accountId);
        return ResponseEntity.noContent().build();
    }

    /** Full replacement; a null budget or quantity clears it */
    public record AccountSettingsRequest(
            @DecimalMin(value = "0.01") @DecimalMax(value = "1000000000") BigDecimal orderBudget,
            @NotNull @Pattern(regexp = "USA|NSE|ALL") String market,
            @Min(1) @Max(1000000) Integer defaultQuantity) {
    }

    public record AccountSettingsResponse(long accountId, BigDecimal orderBudget, String market, Integer defaultQuantity,
                                          boolean configured, Instant updatedAt) {
        static AccountSettingsResponse from(AccountSettings s) {
            return new AccountSettingsResponse(s.getAccountId(), s.getOrderBudget(), s.getMarket(), s.getDefaultQuantity(),
                s.getUpdatedAt() != null, s.getUpdatedAt());
        }
    }
}
