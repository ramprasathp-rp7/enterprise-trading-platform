package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/*
 Pure validation service for order placement requests.
 
 Validates all 8 business rules in optimized order:
 Rule 8 (Idempotency): Check first - fail fast if duplicate key exists
 Rule 1-2 (Account): Verify account exists and is ACTIVE
 Rule 3 (Instrument): Verify instrument exists and is tradable
 Rule 4-5 (Price/Qty): Verify quantity and price are positive
 Rule 6-7 (Funds/Holdings): Verify sufficient funds or holdings
 
*/

@Service
public class OrderValidationService {

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;

    public OrderValidationService(AccountMapper accountMapper,
                                 InstrumentMapper instrumentMapper,
                                 PositionMapper positionMapper,
                                 OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
    }

    public Order validateAndCreateOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request, "request");

        // Rule 8 (first): the idempotency key must not already have been used
        // Fail fast on duplicate key - don't proceed further
        if (orderMapper.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new DuplicateOrderException(request.idempotencyKey());
        }

        // Rule 1: the account must exist
        Account account = accountMapper.findByAccountId(request.accountId());
        if (account == null) {
            throw new AccountNotFoundException(request.accountId());
        }

        // Rule 2: the account must be ACTIVE
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }

        // Rule 3: the instrument must exist and be tradable
        Instrument instrument = instrumentMapper.findBySymbol(request.symbol());
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException(request.symbol());
        }

        // Rule 4: quantity must be greater than zero
        Integer quantity = request.quantity();
        if (quantity == null || quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }

        // Rule 5: price must be greater than zero
        BigDecimal price = request.price();
        if (price == null || price.signum() <= 0) {
            throw new InvalidOrderException("Price must be greater than zero");
        }

        // Fetch Position for funds/holdings validation
        Position position = positionMapper.findByAccountAndInstrument(account.getId(), instrument.getId());
        if (position == null) {
            position = Position.empty(account.getId(), instrument.getSymbol(), instrument.getId());
        }

        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity));
        OrderSide side = request.side();

        if (side == OrderSide.BUY) {
            // Rule 6: on a BUY, the cash balance must cover quantity * price.
            if (!account.canAfford(notional)) {
                throw new InsufficientFundsException(account.getCashBalance(), notional);
            }
        } else {
            // Rule 7: on a SELL, the held quantity must be at least the order quantity.
            int held = position.getQuantity();
            if (held < quantity) {
                throw new InsufficientHoldingsException(held, quantity);
            }
        }

        // All validations passed; create and return NEW order
        return new Order(account.getId(), instrument.getId(), instrument.getSymbol(), side, quantity, price,
            request.idempotencyKey());
    }
}
