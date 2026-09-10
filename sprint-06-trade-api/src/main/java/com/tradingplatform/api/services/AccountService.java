package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.model.Account;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for account read operations.
 * Orchestrates between controllers and MyBatis mappers for data access.
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * Get account details by internal ID.
     * 
     * @param accountId the internal account ID
     * @return AccountResponse with account details
     * @throws AccountNotFoundException if account does not exist
     */
    public AccountResponse getAccount(long accountId) {
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        // Convert Account model to AccountResponse DTO
        return new AccountResponse(
            account.getId(),
            account.getAccountId(),
            account.getHolderName(),
            account.getCashBalance(),
            account.getStatus(),
            account.getVersion(),
            java.time.Instant.now() // Use current timestamp as lastUpdated
        );
    }

    /**
     * Get account balance by internal ID.
     * 
     * @param accountId the internal account ID
     * @return BalanceResponse with cash balance and currency
     * @throws AccountNotFoundException if account does not exist
     */
    public BalanceResponse getBalance(long accountId) {
        BalanceResponse balance = accountMapper.findBalanceByAccountId(accountId);
        if (balance == null) {
            throw new AccountNotFoundException(accountId);
        }
        return balance;
    }

    /**
     * Get all positions for an account by internal ID.
     * 
     * @param accountId the internal account ID
     * @return List of PositionResponse with holdings
     * @throws AccountNotFoundException if account does not exist
     */
    public List<PositionResponse> getPositions(long accountId) {
        // Verify account exists first
        getAccount(accountId);
        
        List<PositionResponse> positions = accountMapper.findPositionByAccountId(accountId);
        return positions != null ? positions : List.of();
    }

    /**
     * Get order history for an account by internal ID.
     * Supports optional filtering by status and date range.
     * 
     * @param accountId the internal account ID
     * @param status optional order status filter
     * @param from optional start date/time (inclusive)
     * @param to optional end date/time (inclusive)
     * @return list of OrderHistoryEntry ordered by creation time (most recent first)
     * @throws AccountNotFoundException if account does not exist
     */
    public List<OrderHistoryEntry> getOrders(long accountId, Optional<OrderStatus> status,
                                            Optional<OffsetDateTime> from, Optional<OffsetDateTime> to) {
        // Verify account exists by attempting to fetch it
        getAccount(accountId);

        // Convert OffsetDateTime to Instant if present
        Optional<Instant> fromInstant = from.map(OffsetDateTime::toInstant);
        Optional<Instant> toInstant = to.map(OffsetDateTime::toInstant);

        // Route to appropriate mapper method based on provided filters
        if (status.isPresent() && fromInstant.isPresent() && toInstant.isPresent()) {
            return accountMapper.findOrdersByAccountIdAndStatusAndFromAndTo(accountId, status.get(), 
                fromInstant.get(), toInstant.get());
        } else if (status.isPresent()) {
            return accountMapper.findOrdersByAccountIdAndStatus(accountId, status.get());
        } else if (fromInstant.isPresent() && toInstant.isPresent()) {
            return accountMapper.findOrdersByAccountIdAndFromAndTo(accountId, fromInstant.get(), toInstant.get());
        } else {
            return accountMapper.findOrdersByAccountId(accountId);
        }
    }
}
