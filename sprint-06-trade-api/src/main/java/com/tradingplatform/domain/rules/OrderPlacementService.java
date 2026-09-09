package com.tradingplatform.domain.rules;

import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.DomainException;
import com.tradingplatform.domain.exception.DuplicateOrderException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InsufficientFundsException;
import com.tradingplatform.domain.exception.InsufficientHoldingsException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public final class OrderPlacementService {

    /**
     * Validates and admits an order. Returns a NEW order — it does not settle it and does not
     * pick an executed price. The Sprint 7 Trade Executor calls {@link #settle} separately, once
     * it has achieved a real fill against a live quote.
     *
     * <p>On rejection by rules 6–8 (funds, holdings, idempotency), the thrown {@link
     * DomainException} carries the REJECTED order via {@link DomainException#getRejectedOrder()}
     * so the caller can persist it to the audit trail. Rules 1–5 reject before a valid Order can
     * exist at all (see the inline comment below), so those exceptions carry no order.</p>
     */
    public Order placeOrder(PlaceOrderRequest request,
                             Optional<Account> account,
                             Optional<Instrument> instrument,
                             Position position,
                             IdempotencyKeyChecker idempotencyKeyChecker) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(idempotencyKeyChecker, "idempotencyKeyChecker");

        // Rules 1–8 are evaluated in this fixed order because that sequence is a contractual
        // business requirement (e.g. an inactive account must fail ACC-403 before an unaffordable
        // order fails ORD-400) — that's why the rules stay inline rather than being split into
        // separately-testable methods. What IS deliberately separated out of this method is
        // settlement and the FILLED transition: placeOrder only validates and admits (status
        // NEW); moving cash, updating the position, and marking FILLED happen in settle(), against
        // whatever price the order is actually executed at.

        // Rule 1: the account must exist
        Account acc = account.orElseThrow(() -> new AccountNotFoundException(request.getAccountId()));

        // Rule 2: the account must be ACTIVE
        if (!acc.isActive()) {
            throw new AccountNotActiveException(acc.getId(), acc.getStatus());
        }

        // Rule 3: the instrument must exist and be tradable
        Instrument ins = instrument.filter(Instrument::isTradable)
            .orElseThrow(() -> new InstrumentNotFoundException(request.getSymbol()));

        // Rule 4: quantity must be greater than zero
        Integer quantity = request.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }

        // Rule 5: price must be greater than zero
        BigDecimal price = request.getPrice();
        if (price == null || price.signum() <= 0) {
            throw new InvalidOrderException("Price must be greater than zero");
        }

        // Rules 1–5 are exactly the inputs Order's own constructor requires (a resolved account,
        // a resolved instrument, positive quantity, positive price) — so there is no valid Order,
        // and nothing to audit as one, until all five pass. From here the order is well-formed:
        // rules 6–8 can still reject it, but the rejection now happens to a real order, which we
        // keep (REJECTED) instead of letting it vanish into a bare exception.
        OrderSide side = request.getSide();
        Order order = new Order(acc.getId(), ins.getId(), ins.getSymbol(), side, quantity, price,
            request.getIdempotencyKey());

        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity));

        if (side == OrderSide.BUY) {
            // Rule 6: on a BUY, the cash balance must cover quantity * price.
            if (!acc.canAfford(notional)) {
                throw reject(order, new InsufficientFundsException(acc.getCashBalance(), notional));
            }
        } else {
            // Rule 7: on a SELL, the held quantity must be at least the order quantity.
            int held = position.getQuantity();
            if (held < quantity) {
                throw reject(order, new InsufficientHoldingsException(held, quantity));
            }
        }

        // Rule 8: the idempotency key must not already have been used
        try {
            idempotencyKeyChecker.claim(request.getIdempotencyKey());
        } catch (DuplicateOrderException ex) {
            throw reject(order, ex);
        }

        // placeOrder validates and admits the order; it does not settle it — the order returned is NEW.
        return order;
    }

    // Marks the order REJECTED and attaches it to the exception being thrown, so the caller can
    // pull the audit record back out of a caught DomainException.
    private static DomainException reject(Order order, DomainException exception) {
        order.markRejected();
        return exception.withRejectedOrder(order);
    }

    /**
     * Settles a NEW order at its executed price: moves cash, updates the position and marks the order FILLED.
     *
     * @throws IllegalStateException if the order is not currently NEW
     */
    public void settle(Account acc, Position position, Order order, BigDecimal executedPrice) {
        Objects.requireNonNull(acc, "acc");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(executedPrice, "executedPrice");

        BigDecimal notional = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));

        if (order.getSide() == OrderSide.BUY) {
            acc.debit(notional);
            position.applyBuy(order.getQuantity(), executedPrice);
        } else {
            acc.credit(notional);
            position.applySell(order.getQuantity());
        }

        order.markFilled(executedPrice);
    }
}