package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.OptimisticLockException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderSettlementService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;

    public OrderSettlementService(AccountMapper accountMapper, PositionMapper positionMapper, OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
    }

    public void settle(Order order, BigDecimal executedPrice) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(executedPrice, "executedPrice");

        // Fetch the Account from the database
        Account acc = accountMapper.findByAccountId(order.getAccountId());
        if (acc == null) {
            throw new IllegalStateException("Account not found for settlement: " + order.getAccountId());
        }

        // Fetch or initialize the Position for this account/instrument pair
        Position position = positionMapper.findByAccountAndInstrument(order.getAccountId(), order.getInstrumentId());
        if (position == null) {
            // Position doesn't exist yet; create an empty one
            position = Position.empty(order.getAccountId(), order.getInstrumentId());
        }

        // Calculate notional value of the order (price * quantity)
        BigDecimal notional = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));

        // Apply settlement based on order side (notional only - fees are deducted separately)
        if (order.getSide() == OrderSide.BUY) {
            // For BUY: debit cash (notional only) from account, add holdings to position
            acc.debit(notional);
            position.applyBuy(order.getQuantity(), executedPrice);
        } else {
            // For SELL: credit cash (notional only) to account, remove holdings from position
            acc.credit(notional);
            position.applySell(order.getQuantity());
        }

        // Mark the order as FILLED with the executed price (fee deducted separately)
        order.markFilled(executedPrice);

        // Calculate new versions for optimistic locking
        int newAccountVersion = acc.getVersion() + 1;
        int newPositionVersion = position.getVersion() + 1;

        // Persist all changes back to the database with optimistic lock checks
        // WHERE clause checks: version = #{version} (what we read)
        // SET clause sets: version = #{newVersion} (what we calculated)
        int accountRowsUpdated = accountMapper.updateAccount(acc, newAccountVersion);
        if (accountRowsUpdated == 0) {
            // Another transaction has modified the account since we read it
            throw new OptimisticLockException("Account", order.getAccountId());
        }

        int positionRowsUpdated = positionMapper.updatePosition(position, newPositionVersion);
        if (positionRowsUpdated == 0) {
            // Another transaction has modified the position since we read it
            throw new OptimisticLockException("Position", order.getAccountId() + ":" + order.getInstrumentId());
        }

        orderMapper.updateOrderStatus(order.getId(), OrderStatus.FILLED, executedPrice);
    }
}

