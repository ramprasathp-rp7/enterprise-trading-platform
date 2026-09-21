package com.tradingplatform.services;

import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.PositionMapper;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.exception.OptimisticLockException;
import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Feeable;
import com.tradingplatform.domain.Order;
import com.tradingplatform.domain.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class OrderSettlementService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;

    public OrderSettlementService(AccountMapper accountMapper, PositionMapper positionMapper) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
    }

    public java.math.BigDecimal settle(Order order, BigDecimal executedPrice, Feeable feeable) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(executedPrice, "executedPrice");
        Objects.requireNonNull(feeable, "feeable");

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

        // Calculate transaction fee based on asset type
        BigDecimal fee = feeable.calculateFee(notional);

        // Apply settlement based on order side
        if (order.getSide() == OrderSide.BUY) {
            // For BUY: debit cash (notional + fee) from account, add holdings to position
            BigDecimal totalDebit = notional.add(fee);
            acc.debit(totalDebit);
            position.applyBuy(order.getQuantity(), executedPrice);
        } else {
            // For SELL: credit cash (notional - fee) to account, remove holdings from position
            BigDecimal totalCredit = notional.subtract(fee);
            acc.credit(totalCredit);
            position.applySell(order.getQuantity());
        }

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
        // Return the calculated fee so callers can persist it with the order
        return fee;
    }
}

