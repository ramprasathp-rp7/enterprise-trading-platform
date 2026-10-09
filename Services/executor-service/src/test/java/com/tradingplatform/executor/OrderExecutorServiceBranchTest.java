package com.tradingplatform.executor;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
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
import com.tradingplatform.domain.PendingOutcome;
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
    void noPriceRejectionIsPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.NO_PRICE);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("No price available");
        verify(publisher).publishOutcome(rejected(order.getId(), "No price available"));
    }

    @Test
    void untradableInstrumentRejectionIsPublished() throws Exception {
        when(instrumentMapper.findById(100L)).thenReturn(
            Instrument.delisted(100L, "AAPL", "Apple", AssetClass.EQUITY, "USD"));

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verify(publisher).publishOutcome(rejected(order.getId(), "Instrument is not tradable"));
    }

    @Test
    void suspendedAccountRejectionIsPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        when(accountMapper.findByAccountId(1L)).thenReturn(null);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verify(publisher).publishOutcome(rejected(order.getId(), "Suspended Account"));
    }

    @Test
    void rejectNewPublishesOnlyWhenTheOrderWasStillNew() {
        executor.rejectNew(order.getId(), 1L, "Pricing service unavailable: down", QUOTED, ORIGINAL_FEE);
        verify(publisher).publishOutcome(rejected(order.getId(), "Pricing service unavailable: down"));

        // e.g. the customer cancelled it meanwhile: nothing is updated, nothing is published
        clearInvocations(publisher);
        when(orderMapper.updateOrderStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);
        executor.rejectNew(order.getId(), 1L, "Pricing service unavailable: down", QUOTED, ORIGINAL_FEE);
        verifyNoInteractions(publisher);
    }

    @Test
    void fillRuleRejectionIsPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.REJECT);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Order rejected by fill rule");
        verify(publisher).publishOutcome(rejected(order.getId(), "Order rejected by fill rule"));
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
        verifyNoInteractions(settlementService);
        // The rejection reaches trade-events, so the customer is notified
        verify(publisher).publishOutcome(rejected(order.getId(), "Insufficient funds"));
    }

    @Test
    void buyFillSettlesAtAskRoundedToLimitScaleAndUsesDbFee() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        Order persisted = new Order(order.getId(), 1L, 100L, OrderSide.BUY, 10, QUOTED, "key-1",
            Instant.now(), OrderStatus.NEW, null, new BigDecimal("2.25"));
        when(orderMapper.findOrderById(order.getId())).thenReturn(order, persisted);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        BigDecimal executed = new BigDecimal("99.50");
        verify(settlementService).settle(order, executed, BigDecimal.ONE);
        verify(orderMapper).updateOrderStatus(eq(order.getId()), eq(OrderStatus.FILLED), isNull(),
            eq(executed), eq(new BigDecimal("2.25")), any(Instant.class));
        verify(orderMapper).recordFill(order.getId(), BigDecimal.ONE, null);
        verify(publisher).publishOutcome(filled(order.getId(), executed, BigDecimal.ONE));
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

        verify(settlementService).settle(sell, new BigDecimal("99.40"), BigDecimal.ONE);
        verify(orderMapper).updateOrderStatus(eq(sell.getId()), eq(OrderStatus.FILLED), isNull(),
            eq(new BigDecimal("99.40")), eq(ORIGINAL_FEE), any(Instant.class));
    }

    @Test
    void fillOfOrderUpdatedConcurrentlyIsNotPublished() throws Exception {
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        when(orderMapper.updateOrderStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verify(settlementService).settle(eq(order), any(), any());
        verify(publisher, never()).publishOutcome(any());
    }

    // --- multi-currency: INR instrument, USD account ---

    private void inrInstrumentWithUsdInrQuote(String usdInr) throws Exception {
        when(instrumentMapper.findById(100L)).thenReturn(
            Instrument.tradable(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR"));
        quote.setCurrency("INR");
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.of(quote));
        Quote fx = new Quote();
        fx.setSymbol("FX:USDINR");
        fx.setPrice(new BigDecimal(usdInr));
        when(quoteClient.getLatestPrices(java.util.List.of("FX:INRUSD", "FX:USDINR"))).thenReturn(java.util.List.of(fx));
    }

    @Test
    void foreignCurrencyFillSettlesAtFreshFillRateAndRecordsIt() throws Exception {
        inrInstrumentWithUsdInrQuote("80");
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        BigDecimal fillRate = new BigDecimal("0.0125000000"); // 1 / 80
        when(settlementService.settle(eq(order), any(), eq(fillRate))).thenReturn(null);

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verify(settlementService).settle(order, new BigDecimal("99.50"), fillRate);
        verify(orderMapper).recordFill(order.getId(), fillRate, null);
        verify(publisher).publishOutcome(filled(order.getId(), new BigDecimal("99.50"), fillRate));
    }

    @Test
    void adverseFxMoveSinceReservationRejectsForInsufficientFunds() throws Exception {
        // 10 x INR 99.50 at USDINR 0.5 is $1990.00; the account holds $1989.99
        inrInstrumentWithUsdInrQuote("0.5");
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);
        when(accountMapper.findByAccountId(1L)).thenReturn(account("1989.99", AccountStatus.ACTIVE));

        executor.process(order, QUOTED, ORIGINAL_FEE);

        verifyRejected("Insufficient funds");
        verifyNoInteractions(settlementService);
        // Like every rejection, it is published to trade-events so the customer is notified
        verify(publisher).publishOutcome(rejected(order.getId(), "Insufficient funds"));
    }

    @Test
    void missingFxRateAtFillIsAPricingOutage() throws Exception {
        inrInstrumentWithUsdInrQuote("80");
        when(quoteClient.getLatestPrices(java.util.List.of("FX:INRUSD", "FX:USDINR"))).thenReturn(java.util.List.of());
        when(fillRule.decide(eq(order), any())).thenReturn(Decision.FILL);

        org.junit.jupiter.api.Assertions.assertThrows(com.tradingplatform.quote.PricingUnavailableException.class,
            () -> executor.process(order, QUOTED, ORIGINAL_FEE));
        verifyNoInteractions(settlementService, publisher);
    }

    /** Matches the ORDER_REJECTED outcome published for an order of account 1. */
    private static PendingOutcome rejected(UUID orderId, String reason) {
        return argThat(o -> o.id().equals(orderId) && o.accountId() == 1L && o.status() == OrderStatus.REJECTED
            && QUOTED.equals(o.price()) && o.executedPrice() == null && ORIGINAL_FEE.equals(o.fee())
            && reason.equals(o.rejectedReason()) && o.executedOn() != null && o.fillFxRate() == null);
    }

    /** Matches the ORDER_FILLED outcome published for an order of account 1. */
    private static PendingOutcome filled(UUID orderId, BigDecimal executedPrice, BigDecimal fillFxRate) {
        return argThat(o -> o.id().equals(orderId) && o.accountId() == 1L && o.status() == OrderStatus.FILLED
            && QUOTED.equals(o.price()) && executedPrice.equals(o.executedPrice()) && ORIGINAL_FEE.equals(o.fee())
            && o.rejectedReason() == null && o.executedOn() != null && fillFxRate.equals(o.fillFxRate()));
    }
}
