package com.tradingplatform.executor;

import com.tradingplatform.domain.Order;
import com.tradingplatform.fill.DefaultFillRule;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.service.AccountService;
import com.tradingplatform.service.InstrumentService;
import com.tradingplatform.service.OrderRepository;
import com.tradingplatform.service.OrderSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class OrderExecutorServiceTest {
    QuoteClient quoteClient;
    FillRule fillRule;
    InstrumentService instrumentService;
    AccountService accountService;
    OrderRepository orderRepository;
    OrderSettlementService settlementService;
    OrderExecutorService executor;

    @BeforeEach
    void setup() {
        quoteClient = mock(QuoteClient.class);
        fillRule = new DefaultFillRule();
        instrumentService = mock(InstrumentService.class);
        accountService = mock(AccountService.class);
        orderRepository = mock(OrderRepository.class);
        settlementService = mock(OrderSettlementService.class);
        executor = new OrderExecutorService(quoteClient, fillRule, instrumentService, accountService, orderRepository, settlementService);
    }

    @Test
    void buyLimitAtOrAboveQuoteFillsAtQuote() throws Exception {
        Order o = new Order("1","INFY.NS","BUY",10,new BigDecimal("100.00"));
        when(instrumentService.isTradable("INFY.NS")).thenReturn(true);
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.of(new BigDecimal("99.50")));
        when(accountService.isSuspended(1L)).thenReturn(false);
        when(accountService.getAvailableCash(1L)).thenReturn(new BigDecimal("2000"));

        executor.process("1",1L,o);

        verify(settlementService, times(1)).settle(o, new BigDecimal("99.50"));
        verify(orderRepository).updateStatus("1","FILLED","FILLED_AT_QUOTE");
    }

    @Test
    void orderWithNoPriceIsResolved() throws Exception {
        Order o = new Order("2","INFY.NS","BUY",1,new BigDecimal("100.00"));
        when(instrumentService.isTradable("INFY.NS")).thenReturn(true);
        when(quoteClient.getLatestPrice("INFY.NS")).thenReturn(Optional.empty());

        executor.process("2",2L,o);

        verify(settlementService, never()).settle(any(), any());
        verify(orderRepository).updateStatus("2","REJECTED","NO_PRICE");
    }
}
