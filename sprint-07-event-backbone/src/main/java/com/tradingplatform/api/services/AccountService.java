package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for account read operations.
 * Orchestrates between controllers and MyBatis mappers for data access.
 * Handles conversion from entities to response DTOs.
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;

    public AccountService(AccountMapper accountMapper, InstrumentMapper instrumentMapper) {
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
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
        return mapToAccountResponse(account);
    }

    /**
     * Get account balance by internal ID.
     * 
     * @param accountId the internal account ID
     * @return BalanceResponse with cash balance and currency
     * @throws AccountNotFoundException if account does not exist
     */
    public BalanceResponse getBalance(long accountId) {
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return mapToBalanceResponse(account);
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
        
        List<Position> positions = accountMapper.findPositionByAccountId(accountId);
        return positions != null ? 
            positions.stream()
                .map(this::mapToPositionResponse)
                .toList() 
            : List.of();
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
                                            Optional<Instant> from, Optional<Instant> to) {
        // Verify account exists by attempting to fetch it
        getAccount(accountId);

        // Route to appropriate mapper method based on provided filters
        List<Order> orders;
        if (status.isPresent() && from.isPresent() && to.isPresent()) {
            orders = accountMapper.findOrdersByAccountIdAndStatusAndFromAndTo(accountId, status.get(), 
                from.get(), to.get());
        } else if (status.isPresent()) {
            orders = accountMapper.findOrdersByAccountIdAndStatus(accountId, status.get());
        } else if (from.isPresent() && to.isPresent()) {
            orders = accountMapper.findOrdersByAccountIdAndFromAndTo(accountId, from.get(), to.get());
        } else {
            orders = accountMapper.findOrdersByAccountId(accountId);
        }

        // Convert Order entities to OrderHistoryEntry DTOs
        return orders != null ? 
            orders.stream()
                .map(this::mapToOrderHistoryEntry)
                .toList()
            : List.of();
    }



    /**
     * Convert Account entity to AccountResponse DTO.
     */
    private AccountResponse mapToAccountResponse(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getAccountId(),
            account.getHolderName(),
            account.getCashBalance(),
            account.getStatus(),
            account.getVersion(),
            account.getLastUpdated() != null ? account.getLastUpdated() : Instant.now()
        );
    }

    /**
     * Convert Account entity to BalanceResponse DTO.
     */
    private BalanceResponse mapToBalanceResponse(Account account) {
        return new BalanceResponse(
            account.getId(),
            account.getCashBalance(),
            account.getCurrency(),
            account.getLastUpdated() != null ? account.getLastUpdated() : Instant.now()
        );
    }

    /**
     * Convert Position entity to PositionResponse DTO.
     */
    private PositionResponse mapToPositionResponse(Position position) {
        // Fetch instrument to get symbol for the response
        Instrument instrument = instrumentMapper.findById(position.getInstrumentId());
        String symbol = instrument != null ? instrument.getSymbol() : "UNKNOWN";
        return new PositionResponse(
            position.getAccountId(),
            symbol,
            position.getQuantity(),
            position.getAverageCost()
        );
    }

    /**
     * Convert Order entity to OrderHistoryEntry DTO.
     */
    private OrderHistoryEntry mapToOrderHistoryEntry(Order order) {
        // Fetch instrument to get symbol for the response
        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        String symbol = instrument != null ? instrument.getSymbol() : "UNKNOWN";
        return new OrderHistoryEntry(
            "ORD-" + order.getId().toString(),
            order.getAccountId(),
            symbol,
            order.getSide(),
            order.getQuantity(),
            order.getPrice(),
            order.getExecutedPrice(),
            order.getStatus(),
            order.getIdempotencyKey(),
            order.getCreatedOn()
        );
    }
}
