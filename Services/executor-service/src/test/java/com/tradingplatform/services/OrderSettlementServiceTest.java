package com.tradingplatform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Order;
import com.tradingplatform.domain.Position;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.exception.OptimisticLockException;
import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.PositionMapper;

class OrderSettlementServiceTest {

    private AccountMapper accountMapper;
    private PositionMapper positionMapper;
    private OrderSettlementService service;
    private Account account;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        positionMapper = mock(PositionMapper.class);
        service = new OrderSettlementService(accountMapper, positionMapper);
        account = new Account(1L, "ACC-001", "John", new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, 4);
        when(accountMapper.findByAccountId(1L)).thenReturn(account);
    }

    private Order order(OrderSide side, int qty) {
        return new Order(1L, 100L, side, qty, new BigDecimal("10.00"), "k");
    }

    @Test
    void buyDebitsCashAndUpdatesExistingPosition() {
        Position existing = new Position(1L, 100L, 10, new BigDecimal("8.00"), 2);
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(existing);
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(), anyInt())).thenReturn(1);

        service.settle(order(OrderSide.BUY, 10), new BigDecimal("10.00"));

        assertEquals(new BigDecimal("900.00"), account.getCashBalance());
        assertEquals(20, existing.getQuantity());
        assertEquals(new BigDecimal("9.000000"), existing.getAverageCost());
        verify(accountMapper).updateAccount(account, 5);
        verify(positionMapper).updatePosition(existing, 3);
        verify(positionMapper, never()).insertPosition(any());
    }

    @Test
    void sellCreditsCashAndReducesPosition() {
        Position existing = new Position(1L, 100L, 10, new BigDecimal("8.00"), 1);
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(existing);
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(), anyInt())).thenReturn(1);

        service.settle(order(OrderSide.SELL, 4), new BigDecimal("12.50"));

        assertEquals(new BigDecimal("1050.00"), account.getCashBalance());
        assertEquals(6, existing.getQuantity());
    }

    @Test
    void firstBuyInsertsNewPosition() {
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(null);
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(), eq(1))).thenReturn(0);
        when(positionMapper.insertPosition(any())).thenReturn(1);

        service.settle(order(OrderSide.BUY, 5), new BigDecimal("10.00"));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionMapper).insertPosition(captor.capture());
        assertEquals(5, captor.getValue().getQuantity());
        assertEquals(100L, captor.getValue().getInstrumentId());
    }

    @Test
    void failedInsertOfNewPositionIsReported() {
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(null);
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(), anyInt())).thenReturn(0);
        when(positionMapper.insertPosition(any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
            () -> service.settle(order(OrderSide.BUY, 5), new BigDecimal("10.00")));
    }

    @Test
    void staleAccountVersionRaisesOptimisticLock() {
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(null);
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(0);

        OptimisticLockException ex = assertThrows(OptimisticLockException.class,
            () -> service.settle(order(OrderSide.BUY, 1), new BigDecimal("10.00")));
        assertEquals("Account", ex.getResourceType());
        verify(positionMapper, never()).updatePosition(any(), anyInt());
    }

    @Test
    void staleExistingPositionRaisesOptimisticLock() {
        Position existing = new Position(1L, 100L, 10, new BigDecimal("8.00"), 3);
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(existing);
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(), anyInt())).thenReturn(0);

        OptimisticLockException ex = assertThrows(OptimisticLockException.class,
            () -> service.settle(order(OrderSide.SELL, 1), new BigDecimal("10.00")));
        assertEquals("Position", ex.getResourceType());
        assertEquals("1:100", ex.getResourceId());
    }

    @Test
    void missingAccountIsRejected() {
        when(accountMapper.findByAccountId(1L)).thenReturn(null);
        assertThrows(IllegalStateException.class,
            () -> service.settle(order(OrderSide.BUY, 1), BigDecimal.TEN));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> service.settle(null, BigDecimal.TEN));
        assertThrows(NullPointerException.class, () -> service.settle(order(OrderSide.BUY, 1), null));
    }

    @Test
    void sellingMoreThanHeldPropagatesDomainError() {
        when(positionMapper.findByAccountAndInstrument(1L, 100L)).thenReturn(null);
        assertThrows(IllegalStateException.class,
            () -> service.settle(order(OrderSide.SELL, 1), BigDecimal.TEN));
        verify(accountMapper, never()).updateAccount(any(), anyInt());
    }
}
