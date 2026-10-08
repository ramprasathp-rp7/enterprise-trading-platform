package com.tradingplatform.executor;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.fill.Decision;
import com.tradingplatform.fill.FillRule;
import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.quote.PricingUnavailableException;
import org.springframework.transaction.annotation.Transactional;
import com.tradingplatform.quote.FxRateService;
import com.tradingplatform.quote.QuoteClient;
import org.springframework.beans.factory.annotation.Autowired;
import com.tradingplatform.services.OrderSettlementService;

@Service
public class OrderExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(OrderExecutorService.class);

    private final QuoteClient quoteClient;
    private final FillRule fillRule;
    private final InstrumentMapper instrumentMapper;
    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final OrderSettlementService settlementService;
    private final com.tradingplatform.publisher.TradeEventPublisher tradeEventPublisher;
    private final FxRateService fxRateService;

    public OrderExecutorService(QuoteClient quoteClient, FillRule fillRule, InstrumentMapper instrumentMapper,
                                AccountMapper accountMapper, OrderMapper orderMapper,
                                OrderSettlementService settlementService, com.tradingplatform.publisher.TradeEventPublisher tradeEventPublisher) {
        this(quoteClient, fillRule, instrumentMapper, accountMapper, orderMapper, settlementService,
            tradeEventPublisher, new FxRateService(quoteClient));
    }

    @Autowired
    public OrderExecutorService(QuoteClient quoteClient, FillRule fillRule, InstrumentMapper instrumentMapper,
                                AccountMapper accountMapper, OrderMapper orderMapper,
                                OrderSettlementService settlementService, com.tradingplatform.publisher.TradeEventPublisher tradeEventPublisher,
                                FxRateService fxRateService) {
        this.fxRateService = fxRateService;
        this.quoteClient = quoteClient;
        this.fillRule = fillRule;
        this.instrumentMapper = instrumentMapper;
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.settlementService = settlementService;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    @Transactional
    public void process(Order order, BigDecimal quotedPrice, BigDecimal originalFee) throws PricingUnavailableException {
        UUID orderId = order == null ? null : order.getId();
        long accountId = order == null ? -1L : order.getAccountId();

        if (order != null && orderMapper.findOrderById(orderId) == null) {
            logger.warn("Rejecting order because it does not exist in Postgres: orderId={}", orderId);
            throw new NoSuchElementException("Order not found: " + orderId);
        }

        if (order == null) {
            logger.warn("Rejecting null order payload: orderId={}", orderId);
            // conditional update to avoid double writes
            orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Order is null", null, null, java.time.Instant.now());
            return;
        }

        // Check if order was cancelled
        if (orderMapper.findOrderStatusForUpdate(orderId) == OrderStatus.CANCELLED) {
            logger.info("Skipping execution: order is cancelled orderId={}", orderId);
            return;
        }

        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null || !instrument.isTradable()) {
            logger.warn("Rejecting order because instrument is not tradable: orderId={}, instrumentId={}", orderId, order.getInstrumentId());
            reject(orderId, accountId, "Instrument is not tradable", quotedPrice, originalFee);
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
                reject(orderId, accountId, "No price available", quotedPrice, originalFee);
            }
            case REJECT -> {
                logger.warn("Rejecting order by fill rule: orderId={}", orderId);
                    java.time.Instant executedOn = java.time.Instant.now();
                    int rows = orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, "Order rejected by fill rule", null, null, executedOn);
                    if (rows == 0) {
                        logger.info("Skipping reject update: order not NEW (already processed) orderId={}", orderId);
                        return;
                    }
                    // publish trade event for rejected order
                    tradeEventPublisher.publishTradeEvent(orderId, accountId, OrderStatus.REJECTED, quotedPrice, null, originalFee, "Order rejected by fill rule", executedOn);
            }
                case FILL -> {
                Account account = accountMapper.findByAccountId(accountId);
                    // lock order row and check status to avoid double settlement
                    com.tradingplatform.enums.OrderStatus currentStatus = orderMapper.findOrderStatusForUpdate(orderId);
                    if (currentStatus != com.tradingplatform.enums.OrderStatus.NEW) {
                        logger.info("Skipping settlement: order not NEW (status={}) orderId={}", currentStatus, orderId);
                        return;
                    }
                if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
                    logger.warn("Rejecting order because account is not active: orderId={}, accountId={}", orderId, accountId);
                    reject(orderId, accountId, "Suspended Account", quotedPrice, originalFee);
                } else {
                    // determine executed price based on side (ask for BUY, bid for SELL)
                    com.tradingplatform.quote.model.Quote q = quoted.get();
                    BigDecimal sidePrice = order.getSide() == OrderSide.BUY ? q.getAsk() : q.getBid();
                    if (sidePrice == null) {
                        logger.warn("Rejecting order because quote side price missing: orderId={}", orderId);
                        reject(orderId, accountId, "No price available", quotedPrice, originalFee);
                        return;
                    }
                    BigDecimal executed = sidePrice.setScale(order.getPrice().scale(), java.math.RoundingMode.HALF_UP);

                    // The executed price is in the instrument's currency; cash moves in the account's.
                    // The rate is fetched fresh now: the order may have waited in NEW since placement
                    // (e.g. while the executor was stopped), and this fill rate is the rate of record.
                    BigDecimal fillFxRate = fxRateService.rate(instrumentCurrency(instrument, q), account.getCurrency());

                    if (order.getSide() == OrderSide.BUY) {
                        // Re-check funds at the fill rate: the reservation was made at the placement rate
                        BigDecimal needed = executed.multiply(BigDecimal.valueOf(order.getQuantity())).multiply(fillFxRate)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                        if (!account.canAfford(needed)) {
                            logger.warn("Rejecting order because account cannot afford fill: orderId={}, accountId={}", orderId, accountId);
                            reject(orderId, accountId, "Insufficient funds", quotedPrice, originalFee);
                            return;
                        }
                    }

                    // Settle order - fees are deducted separately (by REST API in SYNC mode, by FeeDeductionService in ASYNC mode)
                    BigDecimal realisedPnl = settlementService.settle(order, executed, fillFxRate);
                    
                    // Fetch the order from database to get the fee (calculated by sprint-07)
                    Order orderFromDb = orderMapper.findOrderById(orderId);
                    BigDecimal fee = (orderFromDb != null) ? orderFromDb.getFee() : null;
                    // If DB hasn't populated a fee yet (sprint-07 behavior), fall back to the original fee
                    if (fee == null) {
                        fee = originalFee;
                    }
                    
                    // Mark order as filled with executed price and fee
                    order.markFilled(executed);
                    
                    java.time.Instant executedOn = java.time.Instant.now();
                    int rows = orderMapper.updateOrderStatus(orderId, OrderStatus.FILLED, null, executed, fee, executedOn);
                    if (rows == 0) {
                        logger.info("Skipping fill update: order not NEW (already processed) orderId={}", orderId);
                        return;
                    }
                    orderMapper.recordFill(orderId, fillFxRate, realisedPnl);
                    logger.info("Filled order successfully: orderId={}, accountId={}, price={}, fillFxRate={}, fee={}", orderId, accountId, executed, fillFxRate, fee);
                    // publish trade event for filled order with quoted price, original fee and the fill rate
                    tradeEventPublisher.publishTradeEvent(orderId, accountId, OrderStatus.FILLED, quotedPrice, executed, originalFee, null, executedOn, fillFxRate);
                }
            }
        }
    }

    /**
     * The instrument's trading currency. Fauxnance is the source of truth: if the quote's currency
     * disagrees with the instruments table, the quote wins (and the mismatch is logged).
     */
    private static String instrumentCurrency(Instrument instrument, com.tradingplatform.quote.model.Quote quote) {
        String quoted = quote.getCurrency();
        if (quoted == null || !quoted.trim().matches("[A-Za-z]{3}")) {
            return instrument.getCurrency();
        }
        if (!quoted.trim().equalsIgnoreCase(instrument.getCurrency())) {
            logger.warn("Quote currency {} differs from instrument currency {} for {}; using the quote's",
                quoted, instrument.getCurrency(), instrument.getSymbol());
        }
        return quoted.trim();
    }

    /**
     * Rejects an order that is still NEW and publishes ORDER_REJECTED; does nothing to an order that
     * already reached another status (e.g. CANCELLED). Used by the order listener when pricing stays
     * unavailable after its retries.
     */
    @Transactional
    public void rejectNew(UUID orderId, long accountId, String reason, BigDecimal quotedPrice, BigDecimal originalFee) {
        reject(orderId, accountId, reason, quotedPrice, originalFee);
    }

    /**
     * Rejects the order and, if this call changed it (it was still NEW), publishes ORDER_REJECTED to
     * trade-events, so every rejection reaches the topic that customer notifications are built from.
     */
    private void reject(UUID orderId, long accountId, String reason, BigDecimal quotedPrice, BigDecimal originalFee) {
        java.time.Instant executedOn = java.time.Instant.now();
        int rows = orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, reason, null, null, executedOn);
        if (rows == 0) {
            logger.info("Skipping reject update: order not NEW (already processed) orderId={}, reason={}", orderId, reason);
            return;
        }
        tradeEventPublisher.publishTradeEvent(orderId, accountId, OrderStatus.REJECTED, quotedPrice, null, originalFee, reason, executedOn);
    }
}
