package com.tradingplatform.strategy.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.strategy.client.OrderPlacementException;
import com.tradingplatform.strategy.client.StrategyTokenIssuer;
import com.tradingplatform.strategy.client.TradeApiOrderClient;
import com.tradingplatform.strategy.indicator.IndicatorResult;
import com.tradingplatform.strategy.mapper.StrategyExecutionMapper;
import com.tradingplatform.strategy.mapper.TradingStrategyMapper;
import com.tradingplatform.strategy.model.ExecutionOutcome;
import com.tradingplatform.strategy.model.StrategyExecution;
import com.tradingplatform.strategy.model.StrategySignal;
import com.tradingplatform.strategy.model.TradingStrategy;

/**
 * Acts on one trigger, in one transaction that holds the strategy row (SELECT ... FOR UPDATE):
 *
 * 1. Disabled? Recorded as SKIPPED_DISABLED and nothing is sent. Because disabling updates the
 *    same row, a disable either lands before the lock (seen here) or waits until this order is done:
 *    a disabled strategy never sends an order afterwards.
 * 2. Guards, checked at the point of decision: a SELL needs the quantity held; a BUY may not take the
 *    position past max_position; the order may not take today's placed notional past max_daily_notional.
 * 3. The order goes to POST /api/v1/orders with a token minted for this strategy, and an idempotency
 *    key derived from the strategy and the quote, so the same trigger can never place two orders.
 * 4. Success resets the failure count; a failure increments it and, at the strategy's limit, turns it off.
 *
 * Every trigger is recorded in STRATEGY_EXECUTIONS with the indicator values and the reason.
 */
@Component
public class StrategyExecutor {

    private static final Logger logger = LoggerFactory.getLogger(StrategyExecutor.class);

    private final TradingStrategyMapper strategyMapper;
    private final StrategyExecutionMapper executionMapper;
    private final InstrumentMapper instrumentMapper;
    private final PositionMapper positionMapper;
    private final StrategyTokenIssuer tokenIssuer;
    private final TradeApiOrderClient orderClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public StrategyExecutor(TradingStrategyMapper strategyMapper, StrategyExecutionMapper executionMapper,
                            InstrumentMapper instrumentMapper, PositionMapper positionMapper,
                            StrategyTokenIssuer tokenIssuer, TradeApiOrderClient orderClient, ObjectMapper objectMapper) {
        this(strategyMapper, executionMapper, instrumentMapper, positionMapper, tokenIssuer, orderClient, objectMapper,
            Clock.systemUTC());
    }

    StrategyExecutor(TradingStrategyMapper strategyMapper, StrategyExecutionMapper executionMapper,
                     InstrumentMapper instrumentMapper, PositionMapper positionMapper,
                     StrategyTokenIssuer tokenIssuer, TradeApiOrderClient orderClient, ObjectMapper objectMapper,
                     Clock clock) {
        this.strategyMapper = strategyMapper;
        this.executionMapper = executionMapper;
        this.instrumentMapper = instrumentMapper;
        this.positionMapper = positionMapper;
        this.tokenIssuer = tokenIssuer;
        this.orderClient = orderClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Acts on a trigger; returns what happened, or null if the quote was already handled */
    @Transactional
    public ExecutionOutcome execute(Long strategyId, IndicatorResult result, PricePoint quote) {
        TradingStrategy strategy = strategyMapper.lockById(strategyId);
        if (strategy == null) {
            return null;
        }
        Instant last = strategy.getLastEvaluatedQuoteAt();
        if (strategy.isActive() && last != null && !quote.at().isAfter(last)) {
            return null;
        }

        boolean buy = result.signal() == StrategySignal.BUY;
        BigDecimal price = quote.executionPrice(buy).setScale(2, RoundingMode.HALF_UP);
        BigDecimal notional = price.multiply(BigDecimal.valueOf(strategy.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        StrategyExecution execution = execution(strategy, result, quote, price, notional);

        if (!strategy.isActive()) {
            return record(execution, ExecutionOutcome.SKIPPED_DISABLED, "Not placed: the strategy was turned off.");
        }
        strategyMapper.recordEvaluation(strategyId, result.state(), quote.at());

        Blocked blocked = guard(strategy, buy, notional);
        if (blocked != null) {
            return record(execution, blocked.outcome(), blocked.reason());
        }

        String idempotencyKey = "STRAT-" + strategyId + "-" + quote.at().toEpochMilli();
        try {
            TradeApiOrderClient.PlacedOrder placed = orderClient.placeOrder(tokenIssuer.issue(strategy),
                strategy.getAccountId(), strategy.getSymbol(), buy ? OrderSide.BUY : OrderSide.SELL,
                strategy.getQuantity(), price, idempotencyKey);
            execution.setOrderId(placed.orderId());
            strategyMapper.resetFailures(strategyId);
            logger.info("Strategy {} placed {} {} {} at {} ({})", strategyId, result.signal(), strategy.getQuantity(),
                strategy.getSymbol(), price, placed.duplicate() ? "already placed" : placed.orderId());
            return record(execution, ExecutionOutcome.PLACED,
                placed.duplicate() ? "Order was already placed by an earlier attempt." : null);
        } catch (OrderPlacementException e) {
            return failed(strategy, execution, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Strategy {} could not place its order", strategyId, e);
            return failed(strategy, execution, "INTERNAL", e.getMessage());
        }
    }

    /** Why a guard stopped the order */
    private record Blocked(ExecutionOutcome outcome, String reason) {
    }

    /** Why the order may not be placed, or null if it may */
    private Blocked guard(TradingStrategy strategy, boolean buy, BigDecimal notional) {
        int held = heldQuantity(strategy);
        if (!buy && held < strategy.getQuantity()) {
            return new Blocked(ExecutionOutcome.SKIPPED_NO_POSITION,
                "Not placed: " + held + " held, " + strategy.getQuantity() + " needed to sell.");
        }
        if (buy && held + strategy.getQuantity() > strategy.getMaxPosition()) {
            return new Blocked(ExecutionOutcome.SKIPPED_LIMIT, "Not placed: would hold "
                + (held + strategy.getQuantity()) + ", over the max position of " + strategy.getMaxPosition() + ".");
        }
        Instant startOfDay = LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal spent = executionMapper.sumPlacedNotionalSince(strategy.getStrategyId(), startOfDay);
        BigDecimal after = (spent != null ? spent : BigDecimal.ZERO).add(notional);
        if (after.compareTo(strategy.getMaxDailyNotional()) > 0) {
            return new Blocked(ExecutionOutcome.SKIPPED_LIMIT, "Not placed: today's orders would total "
                + after.toPlainString() + ", over the daily limit of " + strategy.getMaxDailyNotional().toPlainString() + ".");
        }
        return null;
    }

    private int heldQuantity(TradingStrategy strategy) {
        Instrument instrument = instrumentMapper.findBySymbol(strategy.getSymbol());
        if (instrument == null) {
            return 0;
        }
        Position position = positionMapper.findByAccountAndInstrument(strategy.getAccountId(), instrument.getId());
        return position != null ? position.getQuantity() : 0;
    }

    private ExecutionOutcome failed(TradingStrategy strategy, StrategyExecution execution, String code, String message) {
        execution.setErrorCode(code);
        execution.setErrorMessage(message);
        strategyMapper.recordFailure(strategy.getStrategyId());
        boolean stopped = strategy.getConsecutiveFailures() + 1 >= strategy.getMaxConsecutiveFailures();
        logger.warn("Strategy {} order failed ({}): {}{}", strategy.getStrategyId(), code, message,
            stopped ? "; strategy turned off" : "");
        return record(execution, ExecutionOutcome.FAILED, stopped
            ? "Order failed; the strategy was turned off after " + strategy.getMaxConsecutiveFailures() + " failures in a row."
            : "Order failed.");
    }

    private ExecutionOutcome record(StrategyExecution execution, ExecutionOutcome outcome, String note) {
        execution.setOutcome(outcome);
        if (note != null) {
            execution.setReason(execution.getReason() + " " + note);
        }
        executionMapper.insert(execution);
        return outcome;
    }

    private StrategyExecution execution(TradingStrategy strategy, IndicatorResult result, PricePoint quote,
                                        BigDecimal price, BigDecimal notional) {
        StrategyExecution e = new StrategyExecution();
        e.setStrategyId(strategy.getStrategyId());
        e.setAccountId(strategy.getAccountId());
        e.setSymbol(strategy.getSymbol());
        e.setQuoteReceivedAt(quote.at());
        e.setSignal(result.signal());
        e.setPrice(price);
        e.setQuantity(strategy.getQuantity());
        e.setNotional(notional);
        e.setIndicatorsJson(json(result));
        e.setReason(result.reason());
        return e;
    }

    private String json(IndicatorResult result) {
        try {
            return objectMapper.writeValueAsString(result.values());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
