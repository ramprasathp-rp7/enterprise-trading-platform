package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.exception.OptimisticLockException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.domain.model.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderSettlementService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;
    private final TransactionMapper transactionMapper;

    public OrderSettlementService(AccountMapper accountMapper,
                                  PositionMapper positionMapper,
                                  OrderMapper orderMapper,
                                  TransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.transactionMapper = transactionMapper;
    }

    /** Settles an order whose instrument is in the account's currency (fill rate of 1). */
    public void settle(Order order, BigDecimal executedPrice) {
        settle(order, executedPrice, BigDecimal.ONE);
    }

    /**
     * Settles an order in account currency. The executed price is in the instrument's currency; cash,
     * the BUY/SELL transaction and realised P&L are in the account's, at {@code fillFxRate}.
     */
    public void settle(Order order, BigDecimal executedPrice, BigDecimal fillFxRate) {
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
            // For SELL: book realised P&L in account currency before the holding is reduced
            realisedPnl = executedPriceAccount.subtract(position.getAverageCostAccount())
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
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
            // Check if this is a NEW position (version was 0)
            if (position.getVersion() == 0) {
                // Insert new position (first time this account/instrument pair is settled)
                // This handles the race condition: if another thread inserted between our check and insert,
                // the constraint violation will tell us someone beat us to it
                int positionRowsInserted = positionMapper.insertPosition(position);
                if (positionRowsInserted == 0) {
                    throw new OptimisticLockException("Position", order.getAccountId() + ":" + order.getInstrumentId());
                }
            } else {
                // Existing position but update failed - likely optimistic lock contention
                throw new OptimisticLockException("Position", order.getAccountId() + ":" + order.getInstrumentId());
            }
        }

        TransactionType transactionType = order.getSide() == OrderSide.BUY
            ? TransactionType.BUY
            : TransactionType.SELL;
        TransactionAction transactionAction = order.getSide() == OrderSide.BUY
            ? TransactionAction.DEBIT
            : TransactionAction.CREDIT;
        Transaction settlementTransaction = new Transaction(
            UUID.randomUUID(),
            order.getAccountId(),
            order.getId(),
            transactionType,
            transactionAction,
            notional,
            Instant.now()
        );
        transactionMapper.insertTransaction(settlementTransaction);

        orderMapper.updateOrderStatus(order.getId(), OrderStatus.FILLED, executedPrice);
        orderMapper.recordFill(order.getId(), fillFxRate, realisedPnl);
        order.setFillFxRate(fillFxRate);
        order.setRealisedPnl(realisedPnl);
    }
}

