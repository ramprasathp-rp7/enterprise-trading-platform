package com.tradingplatform.executor;

import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.fill.Decision;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.service.AccountService;
import com.tradingplatform.api.services.OrderSettlementService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderExecutorService {
    private final QuoteClient quoteClient;
    private final FillRule fillRule;
    private final InstrumentMapper instrumentMapper;
    private final AssetFeeMapper assetFeeMapper;
    private final AccountService accountService;
    private final OrderMapper orderMapper;
    private final OrderSettlementService settlementService;

    public OrderExecutorService(QuoteClient quoteClient, FillRule fillRule, InstrumentMapper instrumentMapper,
                                AssetFeeMapper assetFeeMapper, AccountService accountService, OrderMapper orderMapper,
                                OrderSettlementService settlementService) {
        this.quoteClient = quoteClient;
        this.fillRule = fillRule;
        this.instrumentMapper = instrumentMapper;
        this.assetFeeMapper = assetFeeMapper;
        this.accountService = accountService;
        this.orderMapper = orderMapper;
        this.settlementService = settlementService;
    }

    public void process(String orderId, long accountId, Order order) {
        if (order == null) {
            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, null, null);
            return;
        }

        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null || !instrument.isTradable()) {
            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Instrument does not exist or is delisted", null, null);
            return;
        }

        Optional<BigDecimal> quoted;
        try {
            quoted = quoteClient.getLatestPrice(instrument.getSymbol());
        } catch (PricingUnavailableException e) {
            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Attempts have exhausted", null, null);
            return;
        }

        Decision d = fillRule.decide(order, quoted);
        switch (d) {
            case NO_PRICE:
                orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Pricing is unavailable", null, null);
                return;
            case REJECT:
                orderMapper.updateOrderStatus(UUID.fromString(orderId), "Order does not follow the business rules", OrderStatus.REJECTED, null, null);
                return;
            case FILL:
                if (accountService.isSuspended(accountId)) {
                    orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Account has suspended", null, null);
                    return;
                }
                BigDecimal price = quoted.get();
                if (order.getSide() == OrderSide.BUY) {
                    BigDecimal needed = price.multiply(BigDecimal.valueOf(order.getQuantity()));
                    if (accountService.getAvailableCash(accountId).compareTo(needed) < 0) {
                        orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Total price does not meet the required amount" null, null);
                        return;
                    }
                }

                
                AssetFeeStructure feeStructure = assetFeeMapper.findByAssetType(instrument.getAssetClass().name());
                /*  Dont do this. Throw an exception instead (?)
                if (feeStructure == null) {
                    feeStructure = new AssetFeeStructure(instrument.getAssetClass().name(), new BigDecimal("0.00"), new BigDecimal("0.001"));
                }
                */
                settlementService.settle(order, price, feeStructure);
                // settlementService persists filled order; mirror with mapper for completeness
                orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.FILLED, price, order.getFee());
                return;

            
        }
    }
}
