package com.tradingplatform.executor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.AssetFeeMapper;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.services.OrderSettlementService;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.AssetFeeStructure;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;

import com.tradingplatform.fill.Decision;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;

@Service
public class OrderExecutorService {
    private final QuoteClient quoteClient;
    private final FillRule fillRule;
    private final InstrumentMapper instrumentMapper;
    private final AssetFeeMapper assetFeeMapper;
    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final OrderSettlementService settlementService;

    public OrderExecutorService(QuoteClient quoteClient, FillRule fillRule, InstrumentMapper instrumentMapper,
                                AssetFeeMapper assetFeeMapper, AccountMapper accountMapper, OrderMapper orderMapper,
                                OrderSettlementService settlementService) {
        this.quoteClient = quoteClient;
        this.fillRule = fillRule;
        this.instrumentMapper = instrumentMapper;
        this.assetFeeMapper = assetFeeMapper;
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.settlementService = settlementService;
    }

    public void process(String orderId, long accountId, Order order) {
        if (order == null) {
            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Order is null", null, null);
            return;
        }

        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null || !instrument.isTradable()) {
            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Instrument is not tradable", null, null);
            return;
        }

        Optional<BigDecimal> quoted;
        try {
            quoted = quoteClient.getLatestPrice(instrument.getSymbol());
        } catch (PricingUnavailableException e) {
            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Attempts have exhausted",null, null);
            return;
        }

        Decision d = fillRule.decide(order, quoted);
        switch (d) {
            case NO_PRICE -> orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "No price available", null, null);
            case REJECT -> orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Order rejected by fill rule", null, null);
            case FILL -> {
                Account account = accountMapper.findByAccountId(accountId);
                if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
                    orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Suspended Account", null, null);
                } else {
                    BigDecimal price = quoted.get();
                    if (order.getSide() == OrderSide.BUY) {
                        BigDecimal needed = price.multiply(BigDecimal.valueOf(order.getQuantity()));
                        if (!account.canAfford(needed)) {
                            orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.REJECTED, "Insufficient funds", null, null);
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
                    orderMapper.updateOrderStatus(UUID.fromString(orderId), OrderStatus.FILLED, null, price, order.getFee());
                }
            }
        }
    }
}
