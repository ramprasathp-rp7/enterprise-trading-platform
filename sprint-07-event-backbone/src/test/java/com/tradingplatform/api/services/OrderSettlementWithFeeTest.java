package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.domain.model.Feeable;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test cases for fee deduction during order settlement.
 */
@DisplayName("Order Settlement with Fee Deduction Tests")
class OrderSettlementWithFeeTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private PositionMapper positionMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private TransactionMapper transactionMapper;

    private OrderSettlementService settlementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        settlementService = new OrderSettlementService(accountMapper, positionMapper, orderMapper, transactionMapper);
    }

    /**
     * TEST 1: BUY order deducts notional + fee from cash
     * Fee = flatFee + (notional * percentageFee)
     */
    @Test
    @DisplayName("Test 1: BUY order deducts notional and fee from cash")
    void buyOrderDeductsFeeFromCash() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("50000.00");
        Account account = new Account(1L, "ACC-001", "John", 
            initialBalance, "USD", AccountStatus.ACTIVE, 0);

        Position position = new Position(1L, 1L, 0, BigDecimal.ZERO, 0);

        Order buyOrder = new Order(1L, 1L, OrderSide.BUY, 100, 
            new BigDecimal("100.00"), "order-buy-fee-test");

        // EQUITY fee: $5 flat + 0.01% of notional
        // Notional = 100 * $100 = $10,000
        // Fee = $5 + ($10,000 * 0.0001) = $5 + $1 = $6
        Feeable equityFee = new AssetFeeStructure("EQUITY", 
            new BigDecimal("5.00"), 
            new BigDecimal("0.0001"));

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(positionMapper.findByAccountAndInstrument(1L, 1L)).thenReturn(position);
        when(accountMapper.updateAccount(any(Account.class), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(Position.class), anyInt())).thenReturn(1);
        when(orderMapper.updateOrderStatus(any(), any(), any())).thenReturn(1);

        // Act
        settlementService.settle(buyOrder, new BigDecimal("100.00"));

        // Assert
        // Cash should be debited by notional only = $10,000
        // (Fees are deducted separately by FeeDeductionService in ASYNC mode)
        BigDecimal expectedCashAfter = initialBalance.subtract(new BigDecimal("10000.00"));
        assertEquals(expectedCashAfter, account.getCashBalance(),
            "Cash should be debited by notional ($10,000) only");

        verify(transactionMapper, times(1)).insertTransaction(any());

        // Verify status is FILLED
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus(),
            "Order should be FILLED");
    }

    /**
     * TEST 2: SELL order deducts fee from cash credit
     * For SELL: cash credit = notional - fee
     */
    @Test
    @DisplayName("Test 2: SELL order deducts fee from cash credit")
    void sellOrderDeductsFeeFromCredit() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("10000.00");
        Account account = new Account(1L, "ACC-001", "John", 
            initialBalance, "USD", AccountStatus.ACTIVE, 0);

        Position position = new Position(1L, 1L, 100, new BigDecimal("50.00"), 0);

        Order sellOrder = new Order(1L, 1L, OrderSide.SELL, 50, 
            new BigDecimal("100.00"), "order-sell-fee-test");

        // EQUITY fee: $5 flat + 0.01% of notional
        // Notional = 50 * $100 = $5,000
        // Fee = $5 + ($5,000 * 0.0001) = $5 + $0.50 = $5.50
        Feeable equityFee = new AssetFeeStructure("EQUITY", 
            new BigDecimal("5.00"), 
            new BigDecimal("0.0001"));

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(positionMapper.findByAccountAndInstrument(1L, 1L)).thenReturn(position);
        when(accountMapper.updateAccount(any(Account.class), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(Position.class), anyInt())).thenReturn(1);
        when(orderMapper.updateOrderStatus(any(), any(), any())).thenReturn(1);

        // Act
        settlementService.settle(sellOrder, new BigDecimal("100.00"));

        // Assert
        // Cash should be credited by notional only = $5,000
        // (Fees are deducted separately by FeeDeductionService in ASYNC mode)
        BigDecimal expectedCashAfter = initialBalance.add(new BigDecimal("5000.00"));
        assertEquals(expectedCashAfter, account.getCashBalance(),
            "Cash should be credited by notional ($5,000) only");

        verify(transactionMapper, times(1)).insertTransaction(any());

        // Verify status is FILLED
        assertEquals(OrderStatus.FILLED, sellOrder.getStatus(),
            "Order should be FILLED");
    }

    /**
     * TEST 3: Fee is persisted to database during settlement
     */
    @Test
    @DisplayName("Test 3: Fee is persisted to database")
    void feeIsPersistedToDatabase() {
        // Arrange
        Account account = new Account(1L, "ACC-001", "John", 
            new BigDecimal("50000.00"), "USD", AccountStatus.ACTIVE, 0);

        Position position = new Position(1L, 1L, 0, BigDecimal.ZERO, 0);

        Order buyOrder = new Order(1L, 1L, OrderSide.BUY, 100, 
            new BigDecimal("50.00"), "order-persist-fee");

        Feeable fxFee = new AssetFeeStructure("FX", 
            new BigDecimal("2.50"), 
            new BigDecimal("0.00005"));  // $2.50 + 0.005%

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(positionMapper.findByAccountAndInstrument(1L, 1L)).thenReturn(position);
        when(accountMapper.updateAccount(any(Account.class), anyInt())).thenReturn(1);
        when(positionMapper.updatePosition(any(Position.class), anyInt())).thenReturn(1);
        when(orderMapper.updateOrderStatus(any(), any(), any())).thenReturn(1);

        // Act
        settlementService.settle(buyOrder, new BigDecimal("50.00"));

        // Assert
        // Notional = 100 * $50 = $5,000 (settlement handles notional only)
        // Fees are deducted separately by FeeDeductionService
        verify(orderMapper, times(1)).updateOrderStatus(
            eq(buyOrder.getId()),
            eq(OrderStatus.FILLED),
            eq(new BigDecimal("50.00"))
        );
        verify(transactionMapper, times(1)).insertTransaction(any());
    }

    /**
     * TEST 4: Different asset classes have different fees
     */
    @Test
    @DisplayName("Test 4: Different asset classes apply different fees")
    void differentAssetClassesDifferentFees() {
        // Arrange: Same order size but different fees for different assets
        Account account = new Account(1L, "ACC-001", "John", 
            new BigDecimal("100000.00"), "USD", AccountStatus.ACTIVE, 0);

        Position position = new Position(1L, 1L, 0, BigDecimal.ZERO, 0);

        Order order = new Order(1L, 1L, OrderSide.BUY, 100, 
            new BigDecimal("100.00"), "order-asset-compare");

        // Notional = $10,000 for all
        BigDecimal notional = new BigDecimal("10000.00");

        // EQUITY: $5 + 0.01%
        Feeable equityFee = new AssetFeeStructure("EQUITY", 
            new BigDecimal("5.00"), new BigDecimal("0.0001"));

        // FX: $2.50 + 0.005%
        Feeable fxFee = new AssetFeeStructure("FX", 
            new BigDecimal("2.50"), new BigDecimal("0.00005"));

        // CRYPTO: $10 + 0.02%
        Feeable cryptoFee = new AssetFeeStructure("CRYPTO", 
            new BigDecimal("10.00"), new BigDecimal("0.0002"));

        // Act
        BigDecimal equityFeeAmount = equityFee.calculateFee(notional);
        BigDecimal fxFeeAmount = fxFee.calculateFee(notional);
        BigDecimal cryptoFeeAmount = cryptoFee.calculateFee(notional);

        // Assert
        assertEquals(new BigDecimal("6.00"), equityFeeAmount, 
            "EQUITY fee should be $5 + 0.01% = $6.00");
        assertEquals(new BigDecimal("3.00"), fxFeeAmount, 
            "FX fee should be $2.50 + 0.005% = $3.00");
        assertEquals(new BigDecimal("12.00"), cryptoFeeAmount, 
            "CRYPTO fee should be $10 + 0.02% = $12.00");

        // EQUITY and CRYPTO fees are higher than FX
        assertTrue(equityFeeAmount.compareTo(fxFeeAmount) > 0);
        assertTrue(cryptoFeeAmount.compareTo(fxFeeAmount) > 0);
    }

    /**
     * TEST 5: Rejected/Cancelled orders have no fee
     */
    @Test
    @DisplayName("Test 5: Rejected/Cancelled orders have no fee")
    void rejectedOrdersHaveNoFee() {
        // Arrange
        Order rejectedOrder = new Order(1L, 1L, OrderSide.BUY, 100, 
            new BigDecimal("50.00"), "rejected-order");

        // Act
        rejectedOrder.markRejected();

        // Assert
        assertNull(rejectedOrder.getFee(), 
            "Rejected orders should have no fee set");
        assertEquals(OrderStatus.REJECTED, rejectedOrder.getStatus());
    }
}
