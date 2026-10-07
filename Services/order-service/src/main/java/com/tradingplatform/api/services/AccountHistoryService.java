package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountHistoryMapper;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.domain.dto.AccountHistoryResponse;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InvalidHistoryRequestException;
import com.tradingplatform.domain.model.Account;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

//Service for querying historical snapshots of account and position state.

@Service
public class AccountHistoryService {

    static final int MAX_LIMIT = 365;

    private final AccountMapper accountMapper;
    private final AccountHistoryMapper accountHistoryMapper;

    public AccountHistoryService(AccountMapper accountMapper, 
                         AccountHistoryMapper accountHistoryMapper) {
        this.accountMapper = accountMapper;
        this.accountHistoryMapper = accountHistoryMapper;
        
    }

    // Get account history snapshots for a specific account.

    public List<AccountHistoryResponse> getAccountHistory(long accountId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidHistoryRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return accountHistoryMapper.findByAccountId(accountId, limit);
    }

    // Get account history snapshots for a specific account within a date range (start inclusive, end exclusive).

    public List<AccountHistoryResponse> getAccountHistoryRange(long accountId, Instant startDate, Instant endDate) {
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        if (!startDate.isBefore(endDate)) {
            throw new InvalidHistoryRequestException("from must be on or before to");
        }
        return accountHistoryMapper.findByAccountIdAndDateRange(accountId, startDate, endDate);
    }
}
