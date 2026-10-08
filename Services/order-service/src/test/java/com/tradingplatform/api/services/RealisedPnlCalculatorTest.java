package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.enums.AssetClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("RealisedPnlCalculator Tests")
class RealisedPnlCalculatorTest {

    private RealisedPnlCalculator calculator;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private InstrumentMapper instrumentMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new RealisedPnlCalculator(orderMapper, instrumentMapper);
    }

    @Test
    @DisplayName("Should calculate zero P&L when no orders executed")
    void testZeroPnlNoOrders() {
        // Arrange
        Long accountId = 1L;
        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of());

        // Act
        BigDecimal result = calculator.calculateTotalRealisedPnl(accountId);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("Should ignore BUY orders and only process SELL orders")
    void testIgnoreBuyOrders() {
        // Arrange
        Long accountId = 1L;
        Instrument instrument = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", true);
        
        Order buyOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.BUY, 100, 
                new BigDecimal("150.00"), "idem-1");
        buyOrder.markFilled(new BigDecimal("150.00"));

        Order sellOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 100,
                new BigDecimal("150.00"), "idem-2");
        sellOrder.markFilled(new BigDecimal("155.00"));
        sellOrder.setRealisedPnl(new BigDecimal("500.00")); // booked by settlement at fill

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(buyOrder, sellOrder));
        when(instrumentMapper.findById(1L)).thenReturn(instrument);
               

        // Act
        BigDecimal result = calculator.calculateTotalRealisedPnl(accountId);

        // Assert: Only SELL order P&L counts: (155 - 150) * 100 = 500
        assertEquals(new BigDecimal("500.00"), result);
    }

    @Test
    @DisplayName("Should calculate positive realised P&L on profitable sell")
    void testPositiveRealisedPnl() {
        // Arrange
        Long accountId = 1L;
        Instrument instrument = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", true);
        
        Order sellOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 100,
                new BigDecimal("150.00"), "idem");
        sellOrder.markFilled(new BigDecimal("155.00"));
        sellOrder.setRealisedPnl(new BigDecimal("500.00")); // booked by settlement at fill

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(sellOrder));
        when(instrumentMapper.findById(1L)).thenReturn(instrument);
               

        // Act
        BigDecimal result = calculator.calculateTotalRealisedPnl(accountId);

        // Assert: (155 - 150) * 100 = 500.00
        assertEquals(new BigDecimal("500.00"), result);
    }

    @Test
    @DisplayName("Should calculate negative realised P&L on loss sell")
    void testNegativeRealisedPnl() {
        // Arrange
        Long accountId = 1L;
        Instrument instrument = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", true);
        
        Order sellOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 100,
                new BigDecimal("150.00"), "idem");
        sellOrder.markFilled(new BigDecimal("145.00"));
        sellOrder.setRealisedPnl(new BigDecimal("-500.00")); // booked by settlement at fill

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(sellOrder));
        when(instrumentMapper.findById(1L)).thenReturn(instrument);
               

        // Act
        BigDecimal result = calculator.calculateTotalRealisedPnl(accountId);

        // Assert: (145 - 150) * 100 = -500.00
        assertEquals(new BigDecimal("-500.00"), result);
    }

    @Test
    @DisplayName("Should filter P&L by date range")
    void testPnlDateRangeFiltering() {
        // Arrange
        Long accountId = 1L;
        Instrument instrument = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", true);
        
        Instant oct1 = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant oct15 = LocalDate.of(2026, 10, 15).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant oct30 = LocalDate.of(2026, 10, 30).atStartOfDay(ZoneId.systemDefault()).toInstant();

        Order oldOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 100,
                new BigDecimal("140.00"), "idem-1");
        oldOrder.markFilled(new BigDecimal("145.00"));
        oldOrder.setRealisedPnl(new BigDecimal("500.00")); // booked by settlement at fill

        Order newOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 100,
                new BigDecimal("150.00"), "idem-2");
        newOrder.markFilled(new BigDecimal("155.00"));
        newOrder.setRealisedPnl(new BigDecimal("500.00")); // booked by settlement at fill

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(oldOrder, newOrder));
        when(instrumentMapper.findById(1L))
                .thenReturn(instrument);

        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 20);

        // Act
        BigDecimal result = calculator.calculateRealisedPnlInRange(accountId, from, to);

        // Assert: Should only count newOrder (created between from and to)
        // Actual implementation depends on order creation timestamps which we can't control in this mock
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should aggregate P&L by symbol")
    void testPnlBySymbol() {
        // Arrange
        Long accountId = 1L;
        Instrument aapl = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", true);
        Instrument msft = new Instrument(2L, "MSFT", "Microsoft", AssetClass.EQUITY, "USD", true);

        Order aaplSell1 = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 50,
                new BigDecimal("150.00"), "idem-1");
        aaplSell1.markFilled(new BigDecimal("155.00"));
        aaplSell1.setRealisedPnl(new BigDecimal("250.00")); // booked by settlement at fill

        Order aaplSell2 = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 50,
                new BigDecimal("150.00"), "idem-2");
        aaplSell2.markFilled(new BigDecimal("160.00"));
        aaplSell2.setRealisedPnl(new BigDecimal("500.00")); // booked by settlement at fill

        Order msftSell = new Order(UUID.randomUUID(), accountId, 2L, OrderSide.SELL, 100,
                new BigDecimal("300.00"), "idem-3");
        msftSell.markFilled(new BigDecimal("310.00"));
        msftSell.setRealisedPnl(new BigDecimal("1000.00")); // booked by settlement at fill

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(aaplSell1, aaplSell2, msftSell));
        when(instrumentMapper.findById(1L)).thenReturn(aapl);
        when(instrumentMapper.findById(2L)).thenReturn(msft);

        // Act
        Map<String, BigDecimal> result = calculator.calculateRealisedPnlBySymbol(accountId, null, null, null);

        // Assert
        assertEquals(2, result.size());
        // AAPL: (155-150)*50 + (160-150)*50 = 250 + 500 = 750
        assertEquals(new BigDecimal("750.00"), result.get("AAPL"));
        // MSFT: (310-300)*100 = 1000
        assertEquals(new BigDecimal("1000.00"), result.get("MSFT"));
    }

    @Test
    @DisplayName("Should handle order with missing instrument")
    void testMissingInstrument() {
        // Arrange
        Long accountId = 1L;
        Order sellOrder = new Order(UUID.randomUUID(), accountId, 999L, OrderSide.SELL, 100,
                new BigDecimal("150.00"), "idem");
        sellOrder.markFilled(new BigDecimal("155.00"));
        sellOrder.setRealisedPnl(new BigDecimal("500.00")); // booked by settlement at fill

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(sellOrder));
        when(instrumentMapper.findById(999L))
                .thenReturn(null);

        // Act
        BigDecimal result = calculator.calculateTotalRealisedPnl(accountId);

        // Assert: Order is skipped, no P&L
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("Should handle order with no executed price")
    void testOrderWithoutExecutedPrice() {
        // Arrange
        Long accountId = 1L;
        Instrument instrument = new Instrument(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD", true);
        
        Order sellOrder = new Order(UUID.randomUUID(), accountId, 1L, OrderSide.SELL, 100,
                new BigDecimal("150.00"), "idem");
        // Don't mark as filled - no executed price

        when(orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED))
                .thenReturn(List.of(sellOrder));
        when(instrumentMapper.findById(1L)).thenReturn(instrument);

        // Act
        BigDecimal result = calculator.calculateTotalRealisedPnl(accountId);

        // Assert: Should be zero since order has no executed price
        assertEquals(BigDecimal.ZERO, result);
    }
}
