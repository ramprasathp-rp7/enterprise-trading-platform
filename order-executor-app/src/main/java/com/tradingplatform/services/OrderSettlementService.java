package com.tradingplatform.services;

import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.mappers.PositionMapper;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.exception.DuplicateDeliveryException;
import com.tradingplatform.exception.OptimisticLockException;
import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Feeable;
import com.tradingplatform.domain.Order;
import com.tradingplatform.domain.Position;
import com.tradingplatform.enums.AccountStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Service
public class OrderSettlementService {
    private static final Logger logger = LoggerFactory.getLogger(OrderSettlementService.class);

    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final PositionMapper positionMapper;

    public OrderSettlementService(AccountMapper accountMapper, OrderMapper orderMapper, PositionMapper positionMapper) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.positionMapper = positionMapper;
    }

    /**
     * Settle an order in a single transaction with guarded state transition.
     * 
     * All three writes happen atomically in this order:
     * 1. Guarded order status update (only if order is still NEW)
     * 2. Account cash update (with optimistic lock check)
     * 3. Position update (with optimistic lock check)
     *
     * If any write fails, the entire transaction rolls back and exception is thrown.
     * This ensures cash and position are never out of sync.
     *
     * @param order The order to settle (must be in NEW status)
     * @param executedPrice The price the order filled at
     * @param feeable The fee structure for calculating transaction fees
     * @throws DuplicateDeliveryException if order is not in NEW status (already processed)
     * @throws OptimisticLockException if account or position version conflict
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void settleOrderInTransaction(Order order, BigDecimal executedPrice, Feeable feeable)
            throws DuplicateDeliveryException, OptimisticLockException {
        
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(executedPrice, "executedPrice");
        Objects.requireNonNull(feeable, "feeable");

        // STEP 1: GUARDED STATE TRANSITION (protect against duplicate delivery)
        // This must be FIRST in the transaction. Returns 0 if order is not NEW (already processed).
        int orderRowsAffected = orderMapper.updateOrderStatusGuarded(
            order.getId(),
            OrderStatus.NEW,           // Expect this status
            OrderStatus.FILLED,        // Change to this
            executedPrice,
            Instant.now(),
            null                       // No reject reason on fill
        );

        if (orderRowsAffected == 0) {
            // Order is not in NEW status - another delivery already processed this
            throw new DuplicateDeliveryException("Order already settled: " + order.getId());
        }

        logger.debug("Guarded order update succeeded for orderId={}, rows affected={}", order.getId(), orderRowsAffected);

        // STEP 2: Fetch fresh account (it may have changed since message was enqueued)
        Account acc = accountMapper.findByAccountId(order.getAccountId());
        if (acc == null) {
            throw new IllegalStateException("Account not found for settlement: " + order.getAccountId());
        }

        // STEP 3: Re-check account is still ACTIVE (status can change while order waits on topic)
        if (acc.getStatus() != AccountStatus.ACTIVE) {
            logger.warn("Account is not active during settlement, rejecting order: orderId={}, accountId={}, status={}",
                order.getId(), order.getAccountId(), acc.getStatus());
            // Rollback the guarded order update we just made
            orderMapper.updateOrderStatusGuarded(
                order.getId(),
                OrderStatus.FILLED,     // Undo: expect what we just set
                OrderStatus.REJECTED,   // Undo: go back to this
                null,
                Instant.now(),
                "Account not active"
            );
            throw new IllegalStateException("Account suspended during settlement: " + order.getAccountId());
        }

        // STEP 4: Fetch or create position
        Position position = positionMapper.findByAccountAndInstrument(order.getAccountId(), order.getInstrumentId());
        if (position == null) {
            position = Position.empty(order.getAccountId(), order.getInstrumentId());
        }

        // STEP 5: Calculate debits/credits
        BigDecimal notional = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal fee = feeable.calculateFee(notional);

        // Apply settlement based on order side
        if (order.getSide() == OrderSide.BUY) {
            BigDecimal totalDebit = notional.add(fee);
            acc.debit(totalDebit);
            position.applyBuy(order.getQuantity(), executedPrice);
        } else {
            BigDecimal totalCredit = notional.subtract(fee);
            acc.credit(totalCredit);
            position.applySell(order.getQuantity());
        }

        // STEP 6: Update account with optimistic lock
        // If fails, transaction rolls back and OptimisticLockException is thrown
        int accountRowsUpdated = accountMapper.updateAccount(acc, acc.getVersion() + 1);
        if (accountRowsUpdated == 0) {
            logger.warn("Optimistic lock failed on account during settlement: orderId={}, accountId={}",
                order.getId(), order.getAccountId());
            throw new OptimisticLockException("Account", order.getAccountId());
        }

        // STEP 7: Update position (wrapped in same transaction)
        // If fails, transaction rolls back and OptimisticLockException is thrown
        int positionRowsUpdated = positionMapper.updatePosition(position, position.getVersion() + 1);
        if (positionRowsUpdated == 0) {
            logger.warn("Optimistic lock failed on position during settlement: orderId={}, accountId={}, instrumentId={}",
                order.getId(), order.getAccountId(), order.getInstrumentId());
            throw new OptimisticLockException("Position", order.getAccountId() + ":" + order.getInstrumentId());
        }

        logger.info("Order settled successfully: orderId={}, accountId={}, executedPrice={}, side={}",
            order.getId(), order.getAccountId(), executedPrice, order.getSide());
    }

    /**
     * Legacy method for backward compatibility.
     * Consider using settleOrderInTransaction() instead for proper duplicate handling.
     */
    public void settle(Order order, BigDecimal executedPrice, Feeable feeable) {
        try {
            settleOrderInTransaction(order, executedPrice, feeable);
        } catch (DuplicateDeliveryException | OptimisticLockException e) {
            throw new RuntimeException("Settlement failed: " + e.getMessage(), e);
        }
    }
}


