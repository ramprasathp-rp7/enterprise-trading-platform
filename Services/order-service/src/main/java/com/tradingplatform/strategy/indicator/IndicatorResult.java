package com.tradingplatform.strategy.indicator;

import java.math.BigDecimal;
import java.util.Map;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategySignal;

/**
 * Outcome of evaluating an indicator over a price series.
 *
 * @param state  state after this evaluation; null when there are too few prices to compute it
 * @param signal BUY / SELL when the state changed in a direction that triggers; null otherwise
 * @param values the numbers behind the state (e.g. both averages, or the bands), for the audit trail
 * @param reason one plain sentence saying what the indicator saw
 */
public record IndicatorResult(IndicatorState state, StrategySignal signal, Map<String, BigDecimal> values, String reason) {

    public static IndicatorResult insufficient(int have, int need) {
        return new IndicatorResult(null, null, Map.of(),
            "Waiting for price history: " + have + " of " + need + " prices so far.");
    }

    public boolean triggered() {
        return signal != null;
    }
}
