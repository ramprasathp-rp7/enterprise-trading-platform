package com.tradingplatform.settings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.settings.mapper.AccountSettingsMapper;
import com.tradingplatform.settings.model.AccountSettings;

/**
 * Reads and writes an account's trading settings. Authorisation is the caller's job: the controller
 * checks the token's accountId claim first. Account existence goes through AccountService (ACC-404).
 */
@Service
public class AccountSettingsService {

    private final AccountSettingsMapper mapper;
    private final AccountService accountService;
    private final Clock clock;

    @Autowired
    public AccountSettingsService(AccountSettingsMapper mapper, AccountService accountService) {
        this(mapper, accountService, Clock.systemUTC());
    }

    AccountSettingsService(AccountSettingsMapper mapper, AccountService accountService, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.accountService = Objects.requireNonNull(accountService);
        this.clock = Objects.requireNonNull(clock);
    }

    /** The stored settings, or the defaults when none are stored */
    public AccountSettings get(long accountId) {
        accountService.getAccount(accountId);
        AccountSettings stored = mapper.findByAccountId(accountId);
        return stored != null ? stored : AccountSettings.defaults(accountId);
    }

    /** Full replacement. Values are already validated by the request (bean validation). */
    public AccountSettings replace(long accountId, BigDecimal orderBudget, String market, Integer defaultQuantity) {
        accountService.getAccount(accountId);
        AccountSettings settings = new AccountSettings(
            accountId,
            orderBudget == null ? null : orderBudget.setScale(2, RoundingMode.HALF_UP),
            market,
            defaultQuantity,
            Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        mapper.upsert(settings);
        return settings;
    }

    /** Back to the defaults */
    public void reset(long accountId) {
        accountService.getAccount(accountId);
        mapper.deleteByAccountId(accountId);
    }
}
