package com.tradingplatform.executor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.AssetFeeMapper;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.mappers.PositionMapper;
import com.tradingplatform.services.OrderSettlementService;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.AssetClass;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.fill.DefaultFillRule;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.quote.model.Quote;

public class OrderExecutorServiceTest {
    QuoteClient quoteClient;
    FillRule fillRule;
    InstrumentMapper instrumentMapper;
    AssetFeeMapper assetFeeMapper;
    AccountMapper accountMapper;
    OrderMapper orderMapper;
    PositionMapper positionMapper;
    OrderSettlementService settlementService;
    OrderExecutorService executor;

    @BeforeEach
    void setup() {
        quoteClient = mock(QuoteClient.class);
        fillRule = new DefaultFillRule();
        instrumentMapper = mock(InstrumentMapper.class);
        accountMapper = mock(AccountMapper.class);
        orderMapper = mock(OrderMapper.class);
        positionMapper = mock(PositionMapper.class);
        // create a lightweight stub of OrderSettlementService to avoid inline-mock issues
        settlementService = mock(OrderSettlementService.class);
        // create a no-op TradeEventPublisher stub to avoid Kafka dependencies in tests
        com.tradingplatform.publisher.TradeEventPublisher tradeEventPublisher = new com.tradingplatform.publisher.TradeEventPublisher(null, new com.fasterxml.jackson.databind.ObjectMapper(), null) {
            @Override
            public java.util.concurrent.CompletableFuture<Boolean> publishOutcome(com.tradingplatform.domain.PendingOutcome outcome) {
                return java.util.concurrent.CompletableFuture.completedFuture(true);  // no-op
            }
        };

        executor = new OrderExecutorService(quoteClient, fillRule, instrumentMapper, accountMapper, orderMapper, settlementService, tradeEventPublisher);
    }

    @Test
    void buyLimitAtOrAboveQuoteFillsAtQuote() throws Exception {
        Order o = new Order(1L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1");
        Instrument instr = new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true);
        Account account = new Account(
            1L,
            "ACC-001",
            "John",
            new BigDecimal("2000"),
            "INR",
            AccountStatus.ACTIVE,
            0
        );
        when(instrumentMapper.findById(100L)).thenReturn(instr);
        Quote q = new Quote();
        q.setAsk(new BigDecimal("99.50"));
        q.setBid(new BigDecimal("99.45"));
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.of(q));
        when(orderMapper.findOrderById(any(java.util.UUID.class))).thenReturn(o);
        when(orderMapper.findOrderStatusForUpdate(any(UUID.class))).thenReturn(OrderStatus.NEW);
        when(orderMapper.updateOrderStatus(any(UUID.class), any(com.tradingplatform.enums.OrderStatus.class), any(), any(), any(), any())).thenReturn(1);
        when(accountMapper.findByAccountId(1L)).thenReturn(account);

        executor.process(o, new BigDecimal("100.00"), java.math.BigDecimal.ZERO);

        verify(orderMapper).updateOrderStatus(
            any(UUID.class),
            eq(OrderStatus.FILLED),
            isNull(String.class),
            eq(new BigDecimal("99.50")),
            eq(java.math.BigDecimal.ZERO),
            any(java.time.Instant.class)
        );
    }

    @Test
    void orderWithNoPriceIsResolved() throws Exception {
        Order o = new Order(2L, 100L, OrderSide.BUY, 1, new BigDecimal("100.00"), "key-2");
        Instrument instr = new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true);
        Account account = new Account(
            2L,
            "ACC-002",
            "Jane",
            new BigDecimal("5000"),
            "INR",
            AccountStatus.ACTIVE,
            0
        );
        when(instrumentMapper.findById(100L)).thenReturn(instr);
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.empty());
        when(orderMapper.findOrderById(any(java.util.UUID.class))).thenReturn(o);
        when(orderMapper.findOrderStatusForUpdate(any(UUID.class))).thenReturn(OrderStatus.NEW);
        when(orderMapper.updateOrderStatus(any(UUID.class), any(com.tradingplatform.enums.OrderStatus.class), any(), any(), any(), any())).thenReturn(1);
        when(accountMapper.findByAccountId(2L)).thenReturn(account);

        executor.process(o, new BigDecimal("100.00"), java.math.BigDecimal.ZERO);

        verify(orderMapper).updateOrderStatus(
            any(UUID.class),
            eq(OrderStatus.REJECTED),
            eq("No price available"),
            isNull(BigDecimal.class),
            isNull(BigDecimal.class),
            any(java.time.Instant.class)
        );
    }

    @Test
    void cancelledOrderIsSkipped() throws Exception {
        Order o = new Order(3L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-3");
        when(orderMapper.findOrderById(any(java.util.UUID.class))).thenReturn(o);
        when(orderMapper.findOrderStatusForUpdate(any(UUID.class))).thenReturn(OrderStatus.CANCELLED);

        executor.process(o, new BigDecimal("100.00"), java.math.BigDecimal.ZERO);

        // Verify that updateOrderStatus was NOT called (order was skipped)
        verify(orderMapper, never()).updateOrderStatus(
            any(UUID.class),
            any(com.tradingplatform.enums.OrderStatus.class),
            any(),
            any(),
            any(),
            any(java.time.Instant.class)
        );
        // Verify that settlement service was NOT called
        verify(settlementService, never()).settle(any(), any());
    }
}
