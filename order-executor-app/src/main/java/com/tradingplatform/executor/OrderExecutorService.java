package com.tradingplatform.executor;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.AssetFeeStructure;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.fill.Decision;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.AssetFeeMapper;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.services.OrderSettlementService;

@Service
public class OrderExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(OrderExecutorService.class);

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

    public void process(Order order) throws PricingUnavailableException {
        UUID orderId = order == null ? null : order.getId();
        long accountId = order == null ? -1L : order.getAccountId();

        if (order != null && orderMapper.findOrderById(orderId) == null) {
            logger.warn("Rejecting order because it does not exist in Postgres: orderId={}", orderId);
            throw new NoSuchElementException("Order not found: " + orderId);
        }

        if (order == null) {
            logger.warn("Rejecting null order payload: orderId={}", orderId);
            orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Order is null", null, null, java.time.Instant.now());
            return;
        }

        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null || !instrument.isTradable()) {
            logger.warn("Rejecting order because instrument is not tradable: orderId={}, instrumentId={}", orderId, order.getInstrumentId());
            orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Instrument is not tradable", null, null, java.time.Instant.now());
            return;
        }

        Optional<com.tradingplatform.quote.model.Quote> quoted;
        try {
            quoted = quoteClient.getLatestPrice(instrument.getSymbol());
        } catch (PricingUnavailableException e) {
            logger.warn("Pricing unavailable while processing order: orderId={}, symbol={}, reason={}", orderId, instrument.getSymbol(), e.getMessage());
            throw e;
        }
        Decision d = fillRule.decide(order, quoted);
        switch (d) {
                case NO_PRICE -> {
                logger.warn("Rejecting order because no price is available: orderId={}", orderId);
                orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "No price available", null, null, java.time.Instant.now());
            }
            case REJECT -> {
                logger.warn("Rejecting order by fill rule: orderId={}", orderId);
                orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Order rejected by fill rule", null, null, java.time.Instant.now());
            }
            case FILL -> {
                Account account = accountMapper.findByAccountId(accountId);
                if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
                    logger.warn("Rejecting order because account is not active: orderId={}, accountId={}", orderId, accountId);
                    orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Suspended Account", null, null, java.time.Instant.now());
                } else {
                    // determine executed price based on side (ask for BUY, bid for SELL)
                    com.tradingplatform.quote.model.Quote q = quoted.get();
                    BigDecimal sidePrice = order.getSide() == OrderSide.BUY ? q.getAsk() : q.getBid();
                    if (sidePrice == null) {
                        logger.warn("Rejecting order because quote side price missing: orderId={}", orderId);
                        orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "No price available", null, null, java.time.Instant.now());
                        return;
                    }
                    BigDecimal executed = sidePrice.setScale(order.getPrice().scale(), java.math.RoundingMode.HALF_UP);

                    if (order.getSide() == OrderSide.BUY) {
                        BigDecimal needed = executed.multiply(BigDecimal.valueOf(order.getQuantity()));
                        if (!account.canAfford(needed)) {
                            logger.warn("Rejecting order because account cannot afford fill: orderId={}, accountId={}", orderId, accountId);
                            orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Insufficient funds", null, null, java.time.Instant.now());
                            return;
                        }
                    }

                    
                    AssetFeeStructure feeStructure = assetFeeMapper.findByAssetType(instrument.getAssetClass().name());
                    /*  Dont do this. Throw an exception instead (?)
                    if (feeStructure == null) {
                        feeStructure = new AssetFeeStructure(instrument.getAssetClass().name(), new BigDecimal("0.00"), new BigDecimal("0.001"));
                    }
                    */
                    java.math.BigDecimal fee = settlementService.settle(order, executed, feeStructure);
                    // set fee on order object so mapper persists correct value
                    order.markFilled(executed, fee);
                    
                    orderMapper.updateOrderStatus(orderId, OrderStatus.FILLED, null, executed, fee, java.time.Instant.now());
                    logger.info("Filled order successfully: orderId={}, accountId={}, price={}", orderId,accountId, executed);
                }
            }
        }
    }
}
