package com.tradingplatform.executor;

import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.fill.DefaultFillRule;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.service.AccountService;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.enums.OrderStatus;
import java.util.UUID;
import com.tradingplatform.api.services.OrderSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class OrderExecutorServiceTest {
    QuoteClient quoteClient;
    FillRule fillRule;
    InstrumentMapper instrumentMapper;
    AssetFeeMapper assetFeeMapper;
    AccountService accountService;
    OrderMapper orderMapper;
    OrderSettlementService settlementService;
    OrderExecutorService executor;

    @BeforeEach
    void setup() {
        quoteClient = mock(QuoteClient.class);
        fillRule = new DefaultFillRule();
        instrumentMapper = mock(InstrumentMapper.class);
        assetFeeMapper = mock(AssetFeeMapper.class);
        accountService = mock(AccountService.class);
        orderMapper = mock(OrderMapper.class);
        settlementService = mock(OrderSettlementService.class);
        executor = new OrderExecutorService(quoteClient, fillRule, instrumentMapper, assetFeeMapper, accountService, orderMapper, settlementService);
    }

    @Test
    void buyLimitAtOrAboveQuoteFillsAtQuote() throws Exception {
        Order o = new Order(1L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1");
        Instrument instr = new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(100L)).thenReturn(instr);
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.of(new BigDecimal("99.50")));
        when(accountService.isSuspended(1L)).thenReturn(false);
        when(accountService.getAvailableCash(1L)).thenReturn(new BigDecimal("2000"));

        executor.process("1",1L,o);

        verify(settlementService, times(1)).settle(o, new BigDecimal("99.50"), any());
        verify(orderMapper).updateOrderStatus(any(UUID.class), eq(OrderStatus.FILLED), eq(new BigDecimal("99.50")), any());
    }

    @Test
    void orderWithNoPriceIsResolved() throws Exception {
        Order o = new Order(2L, 100L, OrderSide.BUY, 1, new BigDecimal("100.00"), "key-2");
        Instrument instr = new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(100L)).thenReturn(instr);
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.empty());

        executor.process("2",2L,o);

        verify(settlementService, never()).settle(any(), any(), any());
        verify(orderMapper).updateOrderStatus(any(UUID.class), eq(OrderStatus.REJECTED), isNull(), isNull());
    }
}
