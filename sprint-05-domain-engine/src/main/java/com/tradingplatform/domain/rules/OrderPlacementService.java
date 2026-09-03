package com.tradingplatform.domain.rules;

import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import com.tradingplatform.domain.exception.AccountNotFoundException;
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

        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity));
        OrderSide side = request.getSide();

        if (side == OrderSide.BUY) {
            // Rule 6: on a BUY, the cash balance must cover quantity * price.
            if (!acc.canAfford(notional)) {
                throw new InsufficientFundsException(acc.getCashBalance(), notional);
            }
        } else {
            // Rule 7: on a SELL, the held quantity must be at least the order quantity.
            int held = position.getQuantity();
            if (held < quantity) {
                throw new InsufficientHoldingsException(held, quantity);
            }
        }

        // Rule 8: the idempotency key must not already have been used
        if (idempotencyKeyChecker.isAlreadyUsed(request.getIdempotencyKey())) {
            throw new DuplicateOrderException(request.getIdempotencyKey());
        }

        Order order = new Order(acc.getId(), ins.getId(), ins.getSymbol(), side, quantity, price,
            request.getIdempotencyKey());

        if (side == OrderSide.BUY) {
            acc.debit(notional);
            position.applyBuy(quantity, price);
        } else {
            acc.credit(notional);
            position.applySell(quantity);
        }

        order.markFilled(price);
        return order;
    }
}
