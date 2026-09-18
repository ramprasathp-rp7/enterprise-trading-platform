package com.tradingplatform.executor;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.tradingplatform.exception.DuplicateDeliveryException;
import com.tradingplatform.exception.OptimisticLockException;
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

    /**
     * Process an order from the orders topic.
     * 
     * Returns OrderExecutionResult containing:
     * - finalStatus: FILLED or REJECTED
     * - executedPrice: price if filled, null if rejected
     * - rejectReason: reason if rejected, null if filled
     * - isDuplicate: true if order was already processed
     *
     * @param orderId The order ID (as string from Kafka)
     * @param accountId The account ID
     * @param order The order object
     * @return OrderExecutionResult with settlement outcome
     * @throws PricingUnavailableException if Fauxnance API is unavailable (transient failure)
     * @throws OptimisticLockException if account/position version conflict (transient, retryable)
     * @throws DuplicateDeliveryException if order already settled (duplicate delivery)
     */
    public OrderExecutionResult process(String orderId, long accountId, Order order)
            throws PricingUnavailableException, OptimisticLockException, DuplicateDeliveryException {
        
        // Check order exists in database
        if (order != null && orderMapper.findOrderById(UUID.fromString(orderId)) == null) {
            logger.warn("Rejecting order because it does not exist in Postgres: orderId={}", orderId);
            updateOrderRejected(UUID.fromString(orderId), "Order not found in database");
            return new OrderExecutionResult(OrderStatus.REJECTED, null, "Order not found in database", false);
        }

        // Check order payload is not null
        if (order == null) {
            logger.warn("Rejecting null order payload: orderId={}", orderId);
            updateOrderRejected(UUID.fromString(orderId), "Order is null");
            return new OrderExecutionResult(OrderStatus.REJECTED, null, "Order is null", false);
        }

        // Check instrument is tradable
        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null || !instrument.isTradable()) {
            logger.warn("Rejecting order because instrument is not tradable: orderId={}, instrumentId={}", orderId, order.getInstrumentId());
            updateOrderRejected(UUID.fromString(orderId), "Instrument is not tradable");
            return new OrderExecutionResult(OrderStatus.REJECTED, null, "Instrument is not tradable", false);
        }

        // Fetch quote from Fauxnance API
        Optional<com.tradingplatform.quote.model.Quote> quoted;
        try {
            quoted = quoteClient.getLatestPrice(instrument.getSymbol());
        } catch (PricingUnavailableException e) {
            logger.warn("Pricing unavailable while processing order: orderId={}, symbol={}, reason={}", orderId, instrument.getSymbol(), e.getMessage());
            throw e;  // Transient failure - listener will retry
        }

        // Apply fill rule
        Decision decision = fillRule.decide(order, quoted);
        switch (decision) {
            case NO_PRICE -> {
                logger.warn("Rejecting order because no price is available: orderId={}", orderId);
                updateOrderRejected(UUID.fromString(orderId), "No price available");
                return new OrderExecutionResult(OrderStatus.REJECTED, null, "No price available", false);
            }
            case REJECT -> {
                logger.warn("Rejecting order by fill rule: orderId={}", orderId);
                updateOrderRejected(UUID.fromString(orderId), "Order rejected by fill rule");
                return new OrderExecutionResult(OrderStatus.REJECTED, null, "Order rejected by fill rule", false);
            }
            case FILL -> {
                // Fresh account check (status can change while order waits on topic)
                Account account = accountMapper.findByAccountId(accountId);
                if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
                    logger.warn("Rejecting order because account is not active: orderId={}, accountId={}", orderId, accountId);
                    updateOrderRejected(UUID.fromString(orderId), "Account not active");
                    return new OrderExecutionResult(OrderStatus.REJECTED, null, "Account not active", false);
                }

                // Get executed price from quote (ask for BUY, bid for SELL)
                com.tradingplatform.quote.model.Quote q = quoted.get();
                BigDecimal sidePrice = order.getSide() == OrderSide.BUY ? q.getAsk() : q.getBid();
                if (sidePrice == null) {
                    logger.warn("Rejecting order because quote side price missing: orderId={}", orderId);
                    updateOrderRejected(UUID.fromString(orderId), "No price available");
                    return new OrderExecutionResult(OrderStatus.REJECTED, null, "No price available", false);
                }

                // Round executed price to match limit price precision
                BigDecimal executedPrice = sidePrice.setScale(order.getPrice().scale(), java.math.RoundingMode.HALF_UP);

                // Check affordability for BUY orders
                if (order.getSide() == OrderSide.BUY) {
                    BigDecimal notional = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
                    // Note: fee not included in affordability check yet; settlement will compute and apply
                    if (!account.canAfford(notional)) {
                        logger.warn("Rejecting order because account cannot afford fill: orderId={}, accountId={}", orderId, accountId);
                        updateOrderRejected(UUID.fromString(orderId), "Insufficient funds");
                        return new OrderExecutionResult(OrderStatus.REJECTED, null, "Insufficient funds", false);
                    }
                }

                // Fetch fee structure
                AssetFeeStructure feeStructure = assetFeeMapper.findByAssetType(instrument.getAssetClass().name());
                if (feeStructure == null) {
                    logger.error("Fee structure not found for asset class: {}", instrument.getAssetClass().name());
                    updateOrderRejected(UUID.fromString(orderId), "Fee structure not found");
                    return new OrderExecutionResult(OrderStatus.REJECTED, null, "Fee structure not found", false);
                }

                // Settle order in transaction (all-or-nothing: order + account + position)
                // If duplicate, settlement will throw DuplicateDeliveryException
                // If optimistic lock fails, settlement will throw OptimisticLockException (retryable)
                try {
                    settlementService.settleOrderInTransaction(order, executedPrice, feeStructure);
                    logger.info("Order filled successfully: orderId={}, accountId={}, price={}", orderId, accountId, executedPrice);
                    return new OrderExecutionResult(OrderStatus.FILLED, executedPrice, null, false);
                } catch (DuplicateDeliveryException e) {
                    // Duplicate: order already settled in NEW status
                    // Return true for isDuplicate so listener doesn't publish event again
                    logger.info("Duplicate delivery detected during settlement: orderId={}, reason={}", orderId, e.getMessage());
                    return new OrderExecutionResult(OrderStatus.FILLED, executedPrice, null, true);
                }
            }
        }

        // Should not reach here
        logger.error("Unexpected state in order processing: orderId={}", orderId);
        return new OrderExecutionResult(OrderStatus.REJECTED, null, "Internal error", false);
    }

    /**
     * Helper method to reject an order by updating its status to REJECTED.
     * This is used for business rejections (instrument not tradable, no price, etc.)
     * that don't go through the full settlement logic.
     */
    private void updateOrderRejected(UUID orderId, String reason) {
        try {
            orderMapper.updateOrderStatus(orderId, OrderStatus.REJECTED, reason, null, null);
        } catch (Exception e) {
            logger.error("Failed to update order status to REJECTED: orderId={}, reason={}, error={}",
                orderId, reason, e.getMessage(), e);
        }
    }
}

