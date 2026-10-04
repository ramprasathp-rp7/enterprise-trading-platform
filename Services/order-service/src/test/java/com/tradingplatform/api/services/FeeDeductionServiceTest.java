package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeeDeductionServiceTest {

    private OrderMapper orderMapper;
    private AccountMapper accountMapper;
    private InstrumentMapper instrumentMapper;
    private com.tradingplatform.api.mappers.AssetFeeMapper assetFeeMapper;
    private TransactionMapper transactionMapper;
    private ObjectMapper objectMapper;
    private FeeDeductionService feeDeductionService;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        accountMapper = mock(AccountMapper.class);
        instrumentMapper = mock(InstrumentMapper.class);
        assetFeeMapper = mock(com.tradingplatform.api.mappers.AssetFeeMapper.class);
        transactionMapper = mock(TransactionMapper.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        feeDeductionService = new FeeDeductionService(
            orderMapper,
            accountMapper,
            instrumentMapper,
            assetFeeMapper,
            transactionMapper,
            objectMapper
        );
    }

    @Test
    void orderFilledBuyInsertsBuyAndFeeTransactions() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, 1L, 2L, OrderSide.BUY, 10, new BigDecimal("100.00"), "idem-buy");
        order.markFilled(new BigDecimal("100.00"));

        Account account = new Account(1L, "ACC-1", "Alice", new BigDecimal("5000.00"), "USD", AccountStatus.ACTIVE, 0);
        Instrument instrument = Instrument.tradable(2L, "AAPL", "Apple", AssetClass.EQUITY, "USD");
        AssetFeeStructure feeStructure = new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.0001"));

        when(orderMapper.findOrderById(orderId)).thenReturn(order);
        when(instrumentMapper.findById(2L)).thenReturn(instrument);
        when(assetFeeMapper.findByAssetType("EQUITY")).thenReturn(feeStructure);
        when(transactionMapper.existsByOrderIdAndTransactionType(orderId, TransactionType.BUY)).thenReturn(false);
        when(transactionMapper.existsByOrderIdAndTransactionType(orderId, TransactionType.FEE)).thenReturn(false);
        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(accountMapper.updateAccount(any(Account.class), anyInt())).thenReturn(1);

        String message = buildOrderFilledMessage(orderId, 1L, "BUY", 10, new BigDecimal("100.00"), Instant.parse("2026-10-03T10:00:00Z"));

        feeDeductionService.handleOrderFilled(message, null);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper, times(2)).insertTransaction(txCaptor.capture());
        assertEquals(TransactionType.BUY, txCaptor.getAllValues().get(0).getTransactionType());
        assertEquals(new BigDecimal("1000.00"), txCaptor.getAllValues().get(0).getAmount());
        assertEquals(TransactionType.FEE, txCaptor.getAllValues().get(1).getTransactionType());
        assertEquals(new BigDecimal("5.10"), txCaptor.getAllValues().get(1).getAmount());
    }

    @Test
    void orderFilledSellInsertsSellTransaction() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, 1L, 2L, OrderSide.SELL, 20, new BigDecimal("50.00"), "idem-sell");
        order.markFilled(new BigDecimal("50.00"));

        Account account = new Account(1L, "ACC-1", "Alice", new BigDecimal("8000.00"), "USD", AccountStatus.ACTIVE, 0);
        Instrument instrument = Instrument.tradable(2L, "AAPL", "Apple", AssetClass.EQUITY, "USD");
        AssetFeeStructure feeStructure = new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.0001"));

        when(orderMapper.findOrderById(orderId)).thenReturn(order);
        when(instrumentMapper.findById(2L)).thenReturn(instrument);
        when(assetFeeMapper.findByAssetType("EQUITY")).thenReturn(feeStructure);
        when(transactionMapper.existsByOrderIdAndTransactionType(orderId, TransactionType.SELL)).thenReturn(false);
        when(transactionMapper.existsByOrderIdAndTransactionType(orderId, TransactionType.FEE)).thenReturn(false);
        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(accountMapper.updateAccount(any(Account.class), anyInt())).thenReturn(1);

        String message = buildOrderFilledMessage(orderId, 1L, "SELL", 20, new BigDecimal("50.00"), Instant.parse("2026-10-03T10:01:00Z"));

        feeDeductionService.handleOrderFilled(message, null);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper, times(2)).insertTransaction(txCaptor.capture());
        assertEquals(TransactionType.SELL, txCaptor.getAllValues().get(0).getTransactionType());
        assertEquals(new BigDecimal("1000.00"), txCaptor.getAllValues().get(0).getAmount());
        assertEquals(TransactionType.FEE, txCaptor.getAllValues().get(1).getTransactionType());
    }

    @Test
    void replayedOrderFilledWithExistingFeeSkipsFeeDebitAndInsert() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, 1L, 2L, OrderSide.BUY, 10, new BigDecimal("100.00"), "idem-replay");
        order.markFilled(new BigDecimal("100.00"));

        Instrument instrument = Instrument.tradable(2L, "AAPL", "Apple", AssetClass.EQUITY, "USD");
        AssetFeeStructure feeStructure = new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.0001"));

        when(orderMapper.findOrderById(orderId)).thenReturn(order);
        when(instrumentMapper.findById(2L)).thenReturn(instrument);
        when(assetFeeMapper.findByAssetType("EQUITY")).thenReturn(feeStructure);
        when(transactionMapper.existsByOrderIdAndTransactionType(orderId, TransactionType.BUY)).thenReturn(false);
        when(transactionMapper.existsByOrderIdAndTransactionType(orderId, TransactionType.FEE)).thenReturn(true);

        String message = buildOrderFilledMessage(orderId, 1L, "BUY", 10, new BigDecimal("100.00"), Instant.parse("2026-10-03T10:02:00Z"));

        feeDeductionService.handleOrderFilled(message, null);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper, times(1)).insertTransaction(txCaptor.capture());
        assertEquals(TransactionType.BUY, txCaptor.getValue().getTransactionType());

        verify(accountMapper, never()).findByAccountId(any());
        verify(accountMapper, never()).updateAccount(any(Account.class), anyInt());
        verify(orderMapper, never()).updateOrderFee(eq(orderId), any(BigDecimal.class));
    }

    @Test
    void nonOrderFilledEventIsIgnored() throws Exception {
        TradeEventPayload payload = new TradeEventPayload(
            UUID.randomUUID().toString(),
            1L,
            "AAPL",
            "BUY",
            10,
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            null,
            new BigDecimal("5.00"),
            "REJECTED",
            "INSUFFICIENT_FUNDS",
            null,
            null,
            null,
            Instant.parse("2026-10-03T10:03:00Z")
        );

        MessageEnvelope<TradeEventPayload> envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            EventType.ORDER_REJECTED,
            Instant.parse("2026-10-03T10:03:00Z"),
            com.tradingplatform.api.events.Source.TRADE_EXECUTOR,
            1,
            payload
        );

        String message = objectMapper.writeValueAsString(envelope);
        feeDeductionService.handleOrderFilled(message, null);

        verify(transactionMapper, never()).insertTransaction(any());
    }

    private String buildOrderFilledMessage(UUID orderId,
                                           Long accountId,
                                           String side,
                                           int quantity,
                                           BigDecimal executedPrice,
                                           Instant eventTime) throws Exception {
        TradeEventPayload payload = new TradeEventPayload(
            orderId.toString(),
            accountId,
            "AAPL",
            side,
            quantity,
            executedPrice,
            executedPrice,
            executedPrice,
            new BigDecimal("5.00"),
            "FILLED",
            null,
            side.equals("BUY") ? executedPrice.multiply(BigDecimal.valueOf(quantity)).negate() : executedPrice.multiply(BigDecimal.valueOf(quantity)),
            side.equals("BUY") ? quantity : 0,
            executedPrice,
            eventTime
        );

        MessageEnvelope<TradeEventPayload> envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            EventType.ORDER_FILLED,
            eventTime,
            com.tradingplatform.api.events.Source.TRADE_EXECUTOR,
            1,
            payload
        );
        return objectMapper.writeValueAsString(envelope);
    }
}
