package com.tradingplatform.executor;

import com.tradingplatform.domain.Order;
import com.tradingplatform.fill.Decision;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.service.AccountService;
import com.tradingplatform.service.InstrumentService;
import com.tradingplatform.service.OrderRepository;
import com.tradingplatform.service.OrderSettlementService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class OrderExecutorService {
    private final QuoteClient quoteClient;
    private final FillRule fillRule;
    private final InstrumentService instrumentService;
    private final AccountService accountService;
    private final OrderRepository orderRepository;
    private final OrderSettlementService settlementService;

    public OrderExecutorService(QuoteClient quoteClient, FillRule fillRule, InstrumentService instrumentService,
                                AccountService accountService, OrderRepository orderRepository,
                                OrderSettlementService settlementService) {
        this.quoteClient = quoteClient;
        this.fillRule = fillRule;
        this.instrumentService = instrumentService;
        this.accountService = accountService;
        this.orderRepository = orderRepository;
        this.settlementService = settlementService;
    }

    public void process(String orderId, long accountId, Order order) {
        if (!instrumentService.isTradable(order.getSymbol())) {
            orderRepository.updateStatus(orderId, "REJECTED", "INSTRUMENT_NOT_TRADABLE");
            return;
        }
        Optional<BigDecimal> quoted;
        try {
            quoted = quoteClient.getLatestPrice(order.getSymbol());
        } catch (PricingUnavailableException e) {
            orderRepository.updateStatus(orderId, "REJECTED", "NO_PRICE");
            return;
        }

        Decision d = fillRule.decide(order, quoted);
        switch (d) {
            case NO_PRICE:
                orderRepository.updateStatus(orderId, "REJECTED", "NO_PRICE");
                return;
            case REJECT:
                orderRepository.updateStatus(orderId, "REJECTED", "NOT_MARKETABLE");
                return;
            case FILL:
                if (accountService.isSuspended(accountId)) {
                    orderRepository.updateStatus(orderId, "REJECTED", "ACCOUNT_SUSPENDED");
                    return;
                }
                if (quoted.isEmpty()) {
                    orderRepository.updateStatus(orderId, "REJECTED", "NO_PRICE");
                    return;
                }
                BigDecimal price = quoted.get();
                if ("BUY".equalsIgnoreCase(order.getSide())) {
                    BigDecimal needed = price.multiply(BigDecimal.valueOf(order.getQuantity()));
                    if (accountService.getAvailableCash(accountId).compareTo(needed) < 0) {
                        orderRepository.updateStatus(orderId, "REJECTED", "INSUFFICIENT_FUNDS");
                        return;
                    }
                }
                settlementService.settle(order, price);
                orderRepository.updateStatus(orderId, "FILLED", "FILLED_AT_QUOTE");
                return;
        }
    }
}
