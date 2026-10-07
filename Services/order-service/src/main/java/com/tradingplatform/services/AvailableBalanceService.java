package com.tradingplatform.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * AvailableBalanceService calculates the available balance for an account.
 * 
 * Available balance accounts for pending (NEW) orders:
 * availableBalance = cashBalance - reservedFunds
 * 
 * where reservedFunds = SUM(notional + fee) for all NEW orders, in account currency
 * (notional = limit price * quantity * the order's placement FX rate)
 * 
 * This prevents double-booking of funds when multiple orders are pending.
 */
@Service
public class AvailableBalanceService {
    
    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;
    
    public AvailableBalanceService(AccountMapper accountMapper, OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
    }
    
    /**
     * Calculate available balance for an account.
     * 
     * @param accountId The account ID
     * @return Available balance (cashBalance - reserved funds for pending orders)
     * @throws IllegalStateException if account not found
     */
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(Long accountId) {
        // Step 1: Fetch the account
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new IllegalStateException("Account not found: " + accountId);
        }
        
        // Step 2: Get all NEW (pending) orders for this account
        List<Order> newOrders = orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.NEW);
        
        // Step 3: Calculate reserved funds (sum of notional + fee for each NEW order)
        BigDecimal reservedFunds = newOrders.stream()
            .map(Order::reservedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Step 4: Calculate available balance
        BigDecimal availableBalance = account.getCashBalance().subtract(reservedFunds);
        
        return availableBalance;
    }
}
