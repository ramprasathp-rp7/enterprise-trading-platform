package com.tradingplatform.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.config.ExecutionMode;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.OptimisticLockException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;

/**
 * The Sprint 6 synchronous path (trading.execution.mode=SYNC): the order is settled inside the
 * request. The characterisation test pins the default ASYNC path; this pins SYNC.
 */
class OrderServiceSyncModeTest {

    private static final PlaceOrderRequest REQUEST =
            new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");

    private OrderValidationService validation;
    private OrderSettlementService settlement;
    private OrderMapper orderMapper;
    private AccountMapper accountMapper;
    private InstrumentMapper instrumentMapper;
    private OrderPublisher publisher;
    private OrderService service;
    private PlatformTransactionManager transactionManager;
    private Order order;

    @BeforeEach
    void setUp() {
        validation = mock(OrderValidationService.class);
        settlement = mock(OrderSettlementService.class);
        orderMapper = mock(OrderMapper.class);
        accountMapper = mock(AccountMapper.class);
        instrumentMapper = mock(InstrumentMapper.class);
        publisher = mock(OrderPublisher.class);
        service = new OrderService(validation, settlement, orderMapper, accountMapper,
                instrumentMapper, mock(AssetFeeMapper.class), mock(AccountAccessAuthorizer.class), publisher);
        ReflectionTestUtils.setField(service, "executionMode", ExecutionMode.SYNC);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service.setTransactionManager(transactionManager);

        order = new Order(1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        when(validation.validateAndCreateOrder(REQUEST)).thenReturn(order);
        when(instrumentMapper.findById(2L)).thenReturn(Instrument.tradable(2L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD"));
    }

    @Test
    void settledOrder_isReturnedFilled_andNeverPublished() {
        Order filled = new Order(order.getId(), 1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        filled.markFilled(new BigDecimal("50.00"));
        when(orderMapper.findOrderById(order.getId())).thenReturn(filled);

        OrderResponse response = service.placeOrder(REQUEST);

        verify(settlement).settle(order, new BigDecimal("50.00"), BigDecimal.ONE);
        verify(orderMapper, never()).rejectOrder(any(), anyString());
        verify(publisher, never()).publishOrderPlaced(any(), any());
        // SYNC orders publish nothing, so no event is left pending for the replay jobs
        verify(orderMapper).insertOrder(any(), anyLong(), anyLong(), any(), anyInt(), any(), any(), any(), any(), eq(false));
        assertEquals(OrderStatus.FILLED, response.status());
        assertEquals("Order executed", response.message());
    }

    @Test
    void failedSettlement_marksTheOrderRejectedWithAReason() {
        doThrow(new IllegalStateException("boom")).when(settlement).settle(any(), any(), any());
        Order rejected = new Order(order.getId(), 1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        rejected.markRejected();
        when(orderMapper.findOrderById(order.getId())).thenReturn(rejected);

        OrderResponse response = service.placeOrder(REQUEST);

        verify(orderMapper).rejectOrder(order.getId(), "SETTLEMENT_FAILED");
        verify(orderMapper, never()).updateOrderStatus(any(), eq(OrderStatus.REJECTED), any());
        assertEquals(OrderStatus.REJECTED, response.status());
        assertEquals("Order rejected", response.message());
    }

    @Test
    void concurrentUpdate_isRecordedAsSuch() {
        doThrow(new OptimisticLockException("Account", 1L)).when(settlement).settle(any(), any(), any());
        Order rejected = new Order(order.getId(), 1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        rejected.markRejected();
        when(orderMapper.findOrderById(order.getId())).thenReturn(rejected);

        service.placeOrder(REQUEST);

        verify(orderMapper).rejectOrder(order.getId(), "CONCURRENT_UPDATE");
    }

    @Test
    void settlement_runsInsideASavepoint_releasedOnSuccess() {
        Order filled = new Order(order.getId(), 1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        filled.markFilled(new BigDecimal("50.00"));
        when(orderMapper.findOrderById(order.getId())).thenReturn(filled);

        service.placeOrder(REQUEST);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        InOrder inOrder = inOrder(transactionManager, settlement, orderMapper);
        inOrder.verify(transactionManager).getTransaction(definition.capture());
        inOrder.verify(settlement).settle(any(), any(), any());
        inOrder.verify(orderMapper).findOrderById(order.getId());
        inOrder.verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
        assertEquals(TransactionDefinition.PROPAGATION_NESTED, definition.getValue().getPropagationBehavior());
    }

    @Test
    void failedSettlement_isRolledBackToTheSavepoint_beforeTheOrderIsRejected() {
        doThrow(new OptimisticLockException("Position", "1:2")).when(settlement).settle(any(), any(), any());
        Order rejected = new Order(order.getId(), 1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        rejected.markRejected();
        when(orderMapper.findOrderById(order.getId())).thenReturn(rejected);

        service.placeOrder(REQUEST);

        InOrder inOrder = inOrder(settlement, transactionManager, orderMapper);
        inOrder.verify(settlement).settle(any(), any(), any());
        inOrder.verify(transactionManager).rollback(any());
        inOrder.verify(orderMapper).rejectOrder(order.getId(), "CONCURRENT_UPDATE");
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void failedFeeCharge_isRolledBackToo() {
        Order withFee = new Order(order.getId(), 1L, 2L, OrderSide.BUY, 2, new BigDecimal("50.00"), "idem-sync-0001");
        withFee.markFilled(new BigDecimal("50.00"), new BigDecimal("5.00"));
        when(validation.validateAndCreateOrder(REQUEST)).thenReturn(withFee);
        when(accountMapper.findByAccountId(1L)).thenReturn(new Account(
                1L, "ACC-001", "Test Customer", new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, 3));
        when(accountMapper.updateAccount(any(), anyInt())).thenReturn(0);
        when(orderMapper.findOrderById(order.getId())).thenReturn(order);

        service.placeOrder(REQUEST);

        InOrder inOrder = inOrder(transactionManager, orderMapper);
        inOrder.verify(transactionManager).rollback(any());
        inOrder.verify(orderMapper).rejectOrder(order.getId(), "CONCURRENT_UPDATE");
        verify(orderMapper, never()).updateOrderFee(any(), any());
    }

    @Test
    void syncModeWithoutATransactionManager_failsLoudlyInsteadOfSettlingUnprotected() {
        OrderService unwired = new OrderService(validation, settlement, orderMapper, accountMapper,
                instrumentMapper, mock(AssetFeeMapper.class), mock(AccountAccessAuthorizer.class), publisher);
        ReflectionTestUtils.setField(unwired, "executionMode", ExecutionMode.SYNC);

        assertThrows(IllegalStateException.class, () -> unwired.placeOrder(REQUEST));
        verify(settlement, never()).settle(any(), any(), any());
    }

    @Test
    void rejectionReasons_fitTheColumnAndFollowTheSeededStyle() {
        for (String reason : new String[] {
                OrderService.rejectionReason(new IllegalStateException()),
                OrderService.rejectionReason(new OptimisticLockException("Account", 1L))}) {
            assertEquals(reason.toUpperCase(), reason);
            assertEquals(true, reason.length() <= 100, "rejected_reason is VARCHAR(100)");
        }
    }
}
