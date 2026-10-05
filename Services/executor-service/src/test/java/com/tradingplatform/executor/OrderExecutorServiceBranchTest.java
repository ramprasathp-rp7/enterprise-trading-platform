package com.tradingplatform.executor;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.AssetClass;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.fill.Decision;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.publisher.TradeEventPublisher;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.quote.model.Quote;
import com.tradingplatform.services.OrderSettlementService;

/**
 * Covers each decision branch of {@link OrderExecutorService#process}, with the fill rule
 * mocked so every outcome can be driven directly.
 */
class OrderExecutorServiceBranchTest {

    private static final BigDecimal QUOTED = new BigDecimal("100.00");
    private static final BigDecimal ORIGINAL_FEE = new BigDecimal("1.50");

    private QuoteClient quoteClient;
    private FillRule fillRule;
    private InstrumentMapper instrumentMapper;
    private AccountMapper accountMapper;
    private OrderMapper orderMapper;
    private OrderSettlementService settlementService;
    private TradeEventPublisher publisher;
    private OrderExecutorService executor;

    private Order order;
    private Quote quote;

    @BeforeEach
    void setUp() throws Exception {
        quoteClient = mock(QuoteClient.class);
        fillRule = mock(FillRule.class);
        instrumentMapper = mock(InstrumentMapper.class);
        accountMapper = mock(AccountMapper.class);
        orderMapper = mock(OrderMapper.class);
        settlementService = mock(OrderSettlementService.class);
        publisher = mock(TradeEventPublisher.class);
        executor = new OrderExecutorService(quoteClient, fillRule, instrumentMapper, accountMapper,
            orderMapper, settlementService, publisher);

        order = new Order(1L, 100L, OrderSide.BUY, 10, QUOTED, "key-1");
        quote = new Quote();
        quote.setAsk(new BigDecimal("99.504"));
        quote.setBid(new BigDecimal("99.40"));

        when(orderMapper.findOrderById(order.getId())).thenReturn(order);
        when(orderMapper.findOrderStatusForUpdate(order.getId())).thenReturn(OrderStatus.NEW);
        when(orderMapper.updateOrderStatus(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(instrumentMapper.findById(100L)).thenReturn(
            Instrument.tradable(100L, "AAPL", "Apple", AssetClass.EQUITY, "USD"));
        when(quoteClient.getLatestPrice("AAPL")).thenReturn(Optional.of(quote));
        when(accountMapper.findByAccountId(1L)).thenReturn(account("5000", AccountStatus.ACTIVE));
    }

    private static Account account(String balance, AccountStatus status) {
        return new Account(1L, "ACC-001", "John", new BigDecimal(balance), "USD", status, 0);
    }

    private void verifyRejected(String reason) {
        verify(orderMapper).updateOrderStatus(eq(order.getId()), eq(OrderStatus.REJECTED), eq(reason),
            isNull(), isNull(), any(Instant.class));
    }

    @Test
    void nullOrderIsRecordedAsRejected() throws Exception {
        executor.process(null, QUOTED, ORIGINAL_FEE);

        verify(orderMapper).updateOrderStatus(isNull(), eq(OrderStatus.REJECTED), eq("Order is null"),
            isNull(), isNull(), any(Instant.class));
        verifyNoInteractions(quoteClient, settlementService, publisher);
    }

    @Test
    void orderMissingFromDatabaseThrows() {
        when(orderMapper.findOrderById(order.getId())).thenReturn(null);

        assertThrows(NoSuchElementException.class, () -> executor.process(order, QUOTED, ORIGINAL_FEE));
        verifyNoInteractions(quoteClient);
    }

    @Test
    void unknownInstrumentIsRejected() throws Exception {
        when(instrumentMapper.findById(100L)).thenReturn(null);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Instrument is not tradable");
        verifyNoInteractions(quoteClient);
    }

    @Test
    void delistedInstrumentIsRejected() throws Exception {
        when(instrumentMapper.findById(100L)).thenReturn(
            Instrument.delisted(100L, "AAPL", "Apple", AssetClass.EQUITY, "USD"));

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Instrument is not tradable");
    }

    @Test
    void pricingOutageIsPropagatedForRetry() throws Exception {
        PricingUnavailableException outage = new PricingUnavailableException("down");
        when(quoteClient.getLatestPrice("AAPL")).thenThrow(outage);

        PricingUnavailableException thrown = assertThrows(PricingUnavailableException.class,
            () -> executor.process(order, QUOTED, ORIGINAL_FEE));
        assertSame(outage, thrown);
        verify(orderMapper, never()).updateOrderStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    void noPriceIsRejectedEvenIfAlreadyProcessed() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.NO_PRICE);
        when(orderMapper.updateOrderStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("No price available");
        verifyNoInteractions(publisher);
    }

    @Test
    void fillRuleRejectionIsPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.REJECT);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Order rejected by fill rule");
        verify(publisher).publishTradeEvent(eq(order.getId()), eq(1L), eq(OrderStatus.REJECTED), eq(QUOTED),
            isNull(), eq(ORIGINAL_FEE), eq("Order rejected by fill rule"), any(Instant.class));
    }

    @Test
    void fillRuleRejectionOfAlreadyProcessedOrderIsNotPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.REJECT);
        when(orderMapper.updateOrderStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyNoInteractions(publisher);
    }

    @Test
    void fillOfOrderNoLongerNewIsSkipped() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        // first lookup is the cancellation check, second is the settlement lock
        when(orderMapper.findOrderStatusForUpdate(order.getId())).thenReturn(OrderStatus.NEW, OrderStatus.FILLED);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyNoInteractions(settlementService, publisher);
        verify(orderMapper, never()).updateOrderStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    void fillForMissingAccountIsRejected() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        when(accountMapper.findByAccountId(1L)).thenReturn(null);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Suspended Account");
        verifyNoInteractions(settlementService);
    }

    @Test
    void fillForSuspendedAccountIsRejected() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        when(accountMapper.findByAccountId(1L)).thenReturn(account("5000", AccountStatus.SUSPENDED));

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Suspended Account");
        verifyNoInteractions(settlementService);
    }

    @Test
    void fillWithoutSidePriceIsRejected() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        quote.setAsk(null);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("No price available");
        verifyNoInteractions(settlementService);
    }

    @Test
    void buyThatAccountCannotAffordIsRejected() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        // 10 x 99.50 = 995.00 needed
        when(accountMapper.findByAccountId(1L)).thenReturn(account("994.99", AccountStatus.ACTIVE));

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Insufficient funds");
        verifyNoInteractions(settlementService, publisher);
    }

    @Test
    void buyFillSettlesAtAskRoundedToLimitScaleAndUsesDbFee() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        Order persisted = new Order(order.getId(), 1L, 100L, OrderSide.BUY, 10, QUOTED, "key-1",
            Instant.now(), OrderStatus.NEW, null, new BigDecimal("2.25"));
        when(orderMapper.findOrderById(order.getId())).thenReturn(order, persisted);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        BigDecimal executed = new BigDecimal("99.50");
        verify(settlementService).settle(order, executed);
        verify(orderMapper).updateOrderStatus(eq(order.getId()), eq(OrderStatus.FILLED), isNull(),
            eq(executed), eq(new BigDecimal("2.25")), any(Instant.class));
        verify(publisher).publishTradeEvent(eq(order.getId()), eq(1L), eq(OrderStatus.FILLED), eq(QUOTED),
            eq(executed), eq(ORIGINAL_FEE), isNull(), any(Instant.class));
    }

    @Test
    void sellFillSettlesAtBidAndFallsBackToOriginalFee() throws Exception {
        Order sell = new Order(1L, 100L, OrderSide.SELL, 5, QUOTED, "key-2");
        when(orderMapper.findOrderById(sell.getId())).thenReturn(sell, (Order) null);
        when(orderMapper.findOrderStatusForUpdate(sell.getId())).thenReturn(OrderStatus.NEW);
        when(fillRule.decide(eq(sell), any())).thenReturn(Decision.FILL);
        // a sell is not subject to the affordability check
        when(accountMapper.findByAccountId(1L)).thenReturn(account("0", AccountStatus.ACTIVE));

        executor.process(sell, QUOTED, ORIGINAL_FEE);

        verify(settlementService).settle(sell, new BigDecimal("99.40"));
        verify(orderMapper).updateOrderStatus(eq(sell.getId()), eq(OrderStatus.FILLED), isNull(),
            eq(new BigDecimal("99.40")), eq(ORIGINAL_FEE), any(Instant.class));
    }

    @Test
    void fillOfOrderUpdatedConcurrentlyIsNotPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        when(orderMapper.updateOrderStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verify(settlementService).settle(eq(order), any());
        verify(publisher, never()).publishTradeEvent(any(), anyLong(), any(), any(), any(), any(), any(), any());
    }
}
