package com.tradingplatform.strategy.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.strategy.dto.CreateStrategyRequest;
import com.tradingplatform.strategy.dto.StrategyExecutionResponse;
import com.tradingplatform.strategy.dto.StrategyResponse;
import com.tradingplatform.strategy.exception.StrategyConflictException;
import com.tradingplatform.strategy.exception.StrategyNotFoundException;
import com.tradingplatform.strategy.mapper.StrategyExecutionMapper;
import com.tradingplatform.strategy.mapper.TradingStrategyMapper;
import com.tradingplatform.strategy.model.StrategyExecution;
import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradingStrategy;

/**
 * Creates, lists, turns on/off and deletes an account's strategies, and reads their executions.
 *
 * Caps: at most trading.strategy.max-active-per-account active strategies per account, and one
 * active rule of each type per symbol. Turning a strategy off takes effect immediately: the update
 * waits for an order in flight on the same strategy, and no order is sent after it.
 */
@Service
public class StrategyService {

    static final int DEFAULT_SHORT_WINDOW = 5;
    static final int DEFAULT_LONG_WINDOW = 20;
    static final int DEFAULT_BAND_WINDOW = 20;
    static final BigDecimal DEFAULT_BAND_K = new BigDecimal("2");
    static final int DEFAULT_MAX_FAILURES = 3;
    static final int MAX_EXECUTIONS = 200;

    private final TradingStrategyMapper strategyMapper;
    private final StrategyExecutionMapper executionMapper;
    private final InstrumentMapper instrumentMapper;
    private final ObjectMapper objectMapper;
    private final int maxActivePerAccount;

    public StrategyService(TradingStrategyMapper strategyMapper,
                           StrategyExecutionMapper executionMapper,
                           InstrumentMapper instrumentMapper,
                           ObjectMapper objectMapper,
                           @Value("${trading.strategy.max-active-per-account:10}") int maxActivePerAccount) {
        this.strategyMapper = strategyMapper;
        this.executionMapper = executionMapper;
        this.instrumentMapper = instrumentMapper;
        this.objectMapper = objectMapper;
        this.maxActivePerAccount = maxActivePerAccount;
    }

    @Transactional(readOnly = true)
    public List<StrategyResponse> getStrategies(long accountId) {
        return strategyMapper.findByAccountId(accountId).stream().map(StrategyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StrategyResponse getStrategy(long accountId, long strategyId) {
        return StrategyResponse.from(find(accountId, strategyId));
    }

    @Transactional
    public StrategyResponse createStrategy(long accountId, CreateStrategyRequest request) {
        String requested = request.symbol().trim().toUpperCase(Locale.ROOT);
        Instrument instrument = instrumentMapper.findBySymbol(requested);
        if (instrument == null) {
            throw new InstrumentNotFoundException(requested);
        }
        if (!instrument.isTradable()) {
            throw new InvalidOrderException(requested + " is not tradable, so a strategy cannot trade it");
        }

        TradingStrategy strategy = new TradingStrategy();
        strategy.setAccountId(accountId);
        strategy.setSymbol(instrument.getSymbol());
        strategy.setStrategyType(request.strategyType());
        if (request.strategyType() == StrategyType.MA_CROSSOVER) {
            int shortWindow = request.shortWindow() != null ? request.shortWindow() : DEFAULT_SHORT_WINDOW;
            int longWindow = request.longWindow() != null ? request.longWindow() : DEFAULT_LONG_WINDOW;
            if (longWindow <= shortWindow) {
                throw new InvalidOrderException("longWindow must be greater than shortWindow");
            }
            strategy.setShortWindow(shortWindow);
            strategy.setLongWindow(longWindow);
        } else {
            strategy.setBandWindow(request.bandWindow() != null ? request.bandWindow() : DEFAULT_BAND_WINDOW);
            strategy.setBandK(request.bandK() != null ? request.bandK() : DEFAULT_BAND_K);
        }
        strategy.setTradeSide(request.tradeSide());
        strategy.setQuantity(request.quantity());
        strategy.setMaxPosition(request.maxPosition());
        strategy.setMaxDailyNotional(request.maxDailyNotional());
        strategy.setMaxConsecutiveFailures(
            request.maxConsecutiveFailures() != null ? request.maxConsecutiveFailures() : DEFAULT_MAX_FAILURES);
        strategy.setActive(true);

        assertCanActivate(accountId, strategy.getSymbol(), strategy.getStrategyType());
        try {
            strategyMapper.insert(strategy);
        } catch (DuplicateKeyException e) {
            throw duplicateRule(strategy);
        }
        return StrategyResponse.from(find(accountId, strategy.getStrategyId()));
    }

    /** Turns a strategy off (reason USER) or back on (subject to the same caps as creating one) */
    @Transactional
    public StrategyResponse setActive(long accountId, long strategyId, boolean active) {
        TradingStrategy strategy = find(accountId, strategyId);
        if (active && !strategy.isActive()) {
            assertCanActivate(accountId, strategy.getSymbol(), strategy.getStrategyType());
        }
        try {
            strategyMapper.updateActive(strategyId, accountId, active);
        } catch (DuplicateKeyException e) {
            throw duplicateRule(strategy);
        }
        return StrategyResponse.from(find(accountId, strategyId));
    }

    @Transactional
    public void deleteStrategy(long accountId, long strategyId) {
        if (strategyMapper.delete(strategyId, accountId) == 0) {
            throw new StrategyNotFoundException(strategyId);
        }
    }

    @Transactional(readOnly = true)
    public List<StrategyExecutionResponse> getExecutions(long accountId, long strategyId, Integer limit) {
        find(accountId, strategyId);
        int capped = limit == null ? 50 : Math.max(1, Math.min(limit, MAX_EXECUTIONS));
        return executionMapper.findByStrategyId(strategyId, capped).stream().map(this::toResponse).toList();
    }

    private TradingStrategy find(long accountId, long strategyId) {
        TradingStrategy strategy = strategyMapper.findById(strategyId, accountId);
        if (strategy == null) {
            throw new StrategyNotFoundException(strategyId);
        }
        return strategy;
    }

    private void assertCanActivate(long accountId, String symbol, StrategyType type) {
        if (strategyMapper.countActiveByAccountId(accountId) >= maxActivePerAccount) {
            throw new StrategyConflictException(
                "At most " + maxActivePerAccount + " strategies can be active at once; turn one off first");
        }
        if (strategyMapper.existsActiveRule(accountId, symbol, type)) {
            throw new StrategyConflictException("An active " + type + " strategy already trades " + symbol);
        }
    }

    private StrategyConflictException duplicateRule(TradingStrategy strategy) {
        return new StrategyConflictException(
            "An active " + strategy.getStrategyType() + " strategy already trades " + strategy.getSymbol());
    }

    private StrategyExecutionResponse toResponse(StrategyExecution e) {
        return new StrategyExecutionResponse(e.getExecutionId(), e.getStrategyId(), e.getSymbol(), e.getQuoteReceivedAt(),
            e.getSignal(), e.getPrice(), e.getQuantity(), e.getNotional(), indicators(e.getIndicatorsJson()),
            e.getReason(), e.getOutcome(), e.getOrderId(), e.getErrorCode(), e.getErrorMessage(), e.getCreatedAt());
    }

    private Map<String, Object> indicators(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
