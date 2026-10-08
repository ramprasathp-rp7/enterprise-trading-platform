package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.DomainException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InsufficientFundsException;
import com.tradingplatform.domain.exception.InsufficientHoldingsException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.services.AvailableBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/*
 Pure validation service for order placement requests.
 
 Validates all 8 business rules in optimized order:
 Rule 1-2 (Account): Verify account exists and is ACTIVE
 Rule 3 (Instrument): Verify instrument exists and is tradable
 Rule 4-5 (Price/Qty): Verify quantity and price are positive
 Rule 6-7 (Funds/Holdings): Verify sufficient funds or holdings
 Rule 8 (Idempotency): Verify if duplicate key exists
 
*/

@Service
public class OrderValidationService {

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final PositionMapper positionMapper;
    private final AssetFeeMapper assetFeeMapper;
    private final AvailableBalanceService availableBalanceService;
    private final FxRateService fxRateService;

    /** For callers that only place orders in the account's own currency. */
    public OrderValidationService(AccountMapper accountMapper,
                                 InstrumentMapper instrumentMapper,
                                 PositionMapper positionMapper,
                                 AssetFeeMapper assetFeeMapper,
                                 AvailableBalanceService availableBalanceService) {
        this(accountMapper, instrumentMapper, positionMapper, assetFeeMapper, availableBalanceService,
            FxRateService.sameCurrencyOnly());
    }

    @Autowired
    public OrderValidationService(AccountMapper accountMapper,
                                 InstrumentMapper instrumentMapper,
                                 PositionMapper positionMapper,
                                 AssetFeeMapper assetFeeMapper,
                                 AvailableBalanceService availableBalanceService,
                                 FxRateService fxRateService) {
        this.fxRateService = fxRateService;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.positionMapper = positionMapper;
        this.assetFeeMapper = assetFeeMapper;
        this.availableBalanceService = availableBalanceService;
    }

    public Order validateAndCreateOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request, "request");

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
            position = Position.empty(account.getId(), instrument.getId());
        }

        OrderSide side = request.side();
        Order order = new Order(account.getId(), instrument.getId(), side, quantity, price,
            request.idempotencyKey());

        // The order is placed in the instrument's currency; the funds check and the reservation are in
        // the account's, at the placement rate. The executor settles at a fresh rate fetched at fill.
        BigDecimal placementFxRate = fxRateService.rate(instrument.getCurrency(), account.getCurrency())
            .map(FxRateService.FxRate::rate)
            .orElseThrow(() -> new InvalidOrderException(
                "FX rate unavailable for " + instrument.getCurrency() + "->" + account.getCurrency()));
        order.setPlacementFxRate(placementFxRate);
        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity)).multiply(placementFxRate)
            .setScale(2, RoundingMode.HALF_UP);

        if (side == OrderSide.BUY) {
            // Calculate fee based on instrument's asset class, on the account-currency notional
            String assetType = instrument.getAssetClass().name();
            AssetFeeStructure feeStructure = assetFeeMapper.findByAssetType(assetType);
            BigDecimal fee = feeStructure != null ? feeStructure.calculateFee(notional) : BigDecimal.ZERO;
            order.setFee(fee);  // Store fee for later use in settlement
            
            // Rule 6: on a BUY, available balance must cover notional + fee
            BigDecimal totalNeeded = notional.add(fee);
            BigDecimal availableBalance = availableBalanceService.getAvailableBalance(account.getId());
            if (availableBalance.compareTo(totalNeeded) < 0) {
                throw reject(order, new InsufficientFundsException(availableBalance, totalNeeded));
            }
        } else {
            // Rule 7: on a SELL, the held quantity must be at least the order quantity.
            int held = position.getQuantity();
            if (held < quantity) {
                throw reject(order, new InsufficientHoldingsException(held, quantity));
            }
        }

        // Rule 8: the idempotency key will be enforced at database INSERT via UNIQUE constraint
        // This validation method returns a NEW order; insertion and idempotency check happens in OrderService
        
        // All validations passed; create and return NEW order
        return order;
    }

    private static DomainException reject(Order order, DomainException exception) {
        order.markRejected();
        return exception.withRejectedOrder(order);
    }
}
