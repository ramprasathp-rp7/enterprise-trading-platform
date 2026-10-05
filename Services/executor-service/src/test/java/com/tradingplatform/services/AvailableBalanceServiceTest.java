package com.tradingplatform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.OrderMapper;

class AvailableBalanceServiceTest {

    private AccountMapper accountMapper;
    private OrderMapper orderMapper;
    private AvailableBalanceService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        orderMapper = mock(OrderMapper.class);
        service = new AvailableBalanceService(accountMapper, orderMapper);
    }

    private Order pending(String price, int qty, String fee) {
        return new Order(UUID.randomUUID(), 1L, 100L, OrderSide.BUY, qty, new BigDecimal(price), "k",
            Instant.now(), OrderStatus.NEW, null, fee == null ? null : new BigDecimal(fee));
    }

    @Test
    void subtractsNotionalAndFeeOfPendingOrders() {
        when(accountMapper.findByAccountId(1L)).thenReturn(
            new Account(1L, "ACC-001", "John", new BigDecimal("1000"), "USD", AccountStatus.ACTIVE, 0));
        when(orderMapper.findOrdersByAccountIdAndStatus(1L, OrderStatus.NEW)).thenReturn(List.of(
            pending("100.00", 5, "10.00"),
            pending("30.00", 10, "9.00")));

        assertEquals(new BigDecimal("181.00"), service.getAvailableBalance(1L));
    }

    @Test
    void pendingOrderWithoutFeeReservesNotionalOnly() {
        when(accountMapper.findByAccountId(1L)).thenReturn(
            new Account(1L, "ACC-001", "John", new BigDecimal("100"), "USD", AccountStatus.ACTIVE, 0));
        when(orderMapper.findOrdersByAccountIdAndStatus(1L, OrderStatus.NEW)).thenReturn(List.of(
            pending("10.00", 2, null)));

        assertEquals(new BigDecimal("80.00"), service.getAvailableBalance(1L));
    }

    @Test
    void noPendingOrdersReturnsCashBalance() {
        when(accountMapper.findByAccountId(1L)).thenReturn(
            new Account(1L, "ACC-001", "John", new BigDecimal("55.5"), "USD", AccountStatus.ACTIVE, 0));
        when(orderMapper.findOrdersByAccountIdAndStatus(1L, OrderStatus.NEW)).thenReturn(List.of());

        assertEquals(new BigDecimal("55.50"), service.getAvailableBalance(1L));
    }

    @Test
    void unknownAccountIsRejected() {
        when(accountMapper.findByAccountId(9L)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.getAvailableBalance(9L));
    }
}
