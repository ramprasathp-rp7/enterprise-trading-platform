package com.tradingplatform.services;

import com.tradingplatform.mappers.AccountMapper;
import com.tradingplatform.mappers.PositionMapper;

import jakarta.transaction.Transactional;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.exception.OptimisticLockException;
import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Order;
import com.tradingplatform.domain.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class OrderSettlementService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;

    public OrderSettlementService(AccountMapper accountMapper, PositionMapper positionMapper) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
    }

    /** Settles an order whose instrument is in the account's currency (fill rate of 1). */
    @Transactional 
    public void settle(Order order, BigDecimal executedPrice) {
        settle(order, executedPrice, BigDecimal.ONE);
    }

    /**
     * Settles an order in account currency. The executed price is in the instrument's currency;
     * cash moves by {@code executedPrice * quantity * fillFxRate}, the rate fetched fresh at fill.
     *
     * @return realised P&L in account currency for a SELL, {@code null} for a BUY
     */
    @Transactional
    public BigDecimal settle(Order order, BigDecimal executedPrice, BigDecimal fillFxRate) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(executedPrice, "executedPrice");
        Objects.requireNonNull(fillFxRate, "fillFxRate");

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

        // Notional in account currency: executed price (instrument currency) converted at the fill rate
        BigDecimal executedPriceAccount = executedPrice.multiply(fillFxRate);
        BigDecimal notional = executedPriceAccount.multiply(BigDecimal.valueOf(order.getQuantity()))
            .setScale(2, RoundingMode.HALF_UP);

        // Apply settlement based on order side (notional only - fees are deducted separately)
        BigDecimal realisedPnl = null;
        if (order.getSide() == OrderSide.BUY) {
            // For BUY: debit cash (notional only) from account, add holdings to position
            acc.debit(notional);
            position.applyBuy(order.getQuantity(), executedPrice, executedPriceAccount);
        } else {
            // For SELL: realised P&L in account currency is booked against the account-currency average
            // cost before the holding is reduced, so it includes FX moves since purchase
            realisedPnl = executedPriceAccount.subtract(position.getAverageCostAccount())
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
            // For SELL: credit cash (notional only) to account, remove holdings from position
            acc.credit(notional);
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
            // Check if this is a NEW position (version was 0)
            if (position.getVersion() == 0) {
                // Insert new position (first time this account/instrument pair is settled)
                int positionRowsInserted = positionMapper.insertPosition(position);
                if (positionRowsInserted == 0) {
                    throw new IllegalStateException("Failed to insert new position for account " + order.getAccountId() + ", instrument " + order.getInstrumentId());
                }
            } else {
                // Existing position but update failed - likely optimistic lock contention
                throw new OptimisticLockException("Position", order.getAccountId() + ":" + order.getInstrumentId());
            }
        }
        return realisedPnl;
    }
}

