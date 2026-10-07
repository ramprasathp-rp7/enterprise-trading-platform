package com.tradingplatform.strategy.engine;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.strategy.indicator.BollingerBands;
import com.tradingplatform.strategy.indicator.IndicatorResult;
import com.tradingplatform.strategy.indicator.MovingAverageCrossover;
import com.tradingplatform.strategy.mapper.TradingStrategyMapper;
import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradingStrategy;

/**
 * Runs active strategies against the real-time price stream.
 *
 * Source: the MarketDataCache, filled from the market-data topic or the Fauxnance refresh (the same
 * quotes the UI shows). The cache is only read. On a fixed interval rather than per quote: quotes
 * arrive every few minutes, and recomputing on each one is mostly noise.
 *
 * Per symbol with active strategies, the quote history becomes a series of distinct prices. Each
 * strategy is evaluated once per new observation: its indicator computes a state, and a change of
 * state in a direction the strategy trades is a trigger, handed to StrategyExecutor (which locks the
 * strategy, applies the guards and places the order). Without a trigger, the new state is stored.
 * One failing strategy never stops the others.
 */
@Component
@ConditionalOnProperty(name = "trading.strategy.enabled", havingValue = "true", matchIfMissing = true)
public class StrategyEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(StrategyEvaluator.class);

    private final MarketDataCache marketDataCache;
    private final TradingStrategyMapper strategyMapper;
    private final StrategyExecutor executor;
    private final long pauseBeforeOrderMs;

    public StrategyEvaluator(MarketDataCache marketDataCache,
                             TradingStrategyMapper strategyMapper,
                             StrategyExecutor executor,
                             @Value("${trading.strategy.pause-before-order-ms:0}") long pauseBeforeOrderMs) {
        this.marketDataCache = marketDataCache;
        this.strategyMapper = strategyMapper;
        this.executor = executor;
        this.pauseBeforeOrderMs = pauseBeforeOrderMs;
    }

    @Scheduled(fixedDelayString = "${trading.strategy.evaluate.interval-ms:60000}",
               initialDelayString = "${trading.strategy.evaluate.initial-delay-ms:30000}")
    public void evaluateAll() {
        List<String> symbols;
        try {
            symbols = strategyMapper.findActiveSymbols();
        } catch (RuntimeException e) {
            logger.warn("Strategy evaluation skipped: could not load active symbols", e);
            return;
        }
        for (String symbol : symbols) {
            try {
                evaluateSymbol(symbol);
            } catch (RuntimeException e) {
                logger.error("Strategy evaluation failed for {}", symbol, e);
            }
        }
    }

    void evaluateSymbol(String symbol) {
        List<PricePoint> series = PricePoint.series(marketDataCache.getQuoteHistory(symbol));
        if (series.isEmpty()) {
            return;
        }
        PricePoint newest = series.get(series.size() - 1);
        List<BigDecimal> prices = series.stream().map(PricePoint::price).toList();

        for (TradingStrategy strategy : strategyMapper.findActiveBySymbol(symbol)) {
            try {
                evaluate(strategy, newest, prices);
            } catch (RuntimeException e) {
                logger.error("Strategy {} evaluation failed", strategy.getStrategyId(), e);
            }
        }
    }

    private void evaluate(TradingStrategy strategy, PricePoint newest, List<BigDecimal> prices) {
        if (strategy.getLastEvaluatedQuoteAt() != null && !newest.at().isAfter(strategy.getLastEvaluatedQuoteAt())) {
            return;
        }
        IndicatorResult result = indicator(strategy, prices);
        if (result.triggered() && strategy.getTradeSide().allows(result.signal())) {
            pauseBeforeOrder();
            executor.execute(strategy.getStrategyId(), result, newest);
            return;
        }
        IndicatorState state = result.state() != null ? result.state() : strategy.getLastState();
        strategyMapper.recordEvaluation(strategy.getStrategyId(), state, newest.at());
    }

    static IndicatorResult indicator(TradingStrategy strategy, List<BigDecimal> prices) {
        if (strategy.getStrategyType() == StrategyType.BOLLINGER) {
            return BollingerBands.evaluate(prices, strategy.getBandWindow(), strategy.getBandK().doubleValue(),
                strategy.getLastState());
        }
        return MovingAverageCrossover.evaluate(prices, strategy.getShortWindow(), strategy.getLongWindow(),
            strategy.getLastState());
    }

    /** Demo aid (trading.strategy.pause-before-order-ms): widens the gap in which a disable can land mid-cycle */
    private void pauseBeforeOrder() {
        if (pauseBeforeOrderMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseBeforeOrderMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
