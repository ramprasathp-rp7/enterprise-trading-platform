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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.AssetFeeMapper;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
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

public class OrderExecutorServiceTest {
    QuoteClient quoteClient;
    FillRule fillRule;
    InstrumentMapper instrumentMapper;
    AssetFeeMapper assetFeeMapper;
    AccountMapper accountMapper;
    OrderMapper orderMapper;
    OrderSettlementService settlementService;
    OrderExecutorService executor;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        quoteClient = mock(QuoteClient.class);
        fillRule = new DefaultFillRule();
        instrumentMapper = mock(InstrumentMapper.class);
        assetFeeMapper = mock(AssetFeeMapper.class);
        accountMapper = mock(AccountMapper.class);
        orderMapper = mock(OrderMapper.class);
        settlementService = mock(OrderSettlementService.class);
        executor = new OrderExecutorService(quoteClient, fillRule, instrumentMapper, assetFeeMapper, accountMapper, orderMapper, settlementService);
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
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.of(new BigDecimal("99.50")));
        when(accountMapper.findByAccountId(1L)).thenReturn(account);

        executor.process("1",1L,o);

        verify(settlementService, times(1)).settle(o, new BigDecimal("99.50"), any());
        verify(orderMapper).updateOrderStatus(any(UUID.class), eq(OrderStatus.FILLED), eq(new BigDecimal("99.50")), any());
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
        when(accountMapper.findByAccountId(2L)).thenReturn(account);

        executor.process("2",2L,o);

        verify(settlementService, never()).settle(any(), any(), any());
        verify(orderMapper).updateOrderStatus(any(UUID.class), eq(OrderStatus.REJECTED), isNull(), isNull());
    }
}
