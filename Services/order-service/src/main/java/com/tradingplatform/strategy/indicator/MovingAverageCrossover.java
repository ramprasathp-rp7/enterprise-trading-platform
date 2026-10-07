package com.tradingplatform.strategy.indicator;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategySignal;

/**
 * Moving-average crossover: compares the short and long simple moving averages of the newest prices.
 * State is ABOVE when the short average is above the long one, BELOW when under it (equal keeps the
 * previous state). A trigger is a crossing: BELOW -> ABOVE is BUY, ABOVE -> BELOW is SELL. The first
 * evaluation only records the state, so a strategy never fires on a crossing it did not see happen.
 */
public final class MovingAverageCrossover {

    private MovingAverageCrossover() {
    }

    public static IndicatorResult evaluate(List<BigDecimal> pricesOldestFirst, int shortWindow, int longWindow,
                                           IndicatorState previous) {
        if (shortWindow < 1 || longWindow <= shortWindow) {
            throw new IllegalArgumentException("Need 1 <= short window < long window");
        }
        if (pricesOldestFirst.size() < longWindow) {
            return IndicatorResult.insufficient(pricesOldestFirst.size(), longWindow);
        }

        double shortMa = Indicators.sma(pricesOldestFirst, shortWindow);
        double longMa = Indicators.sma(pricesOldestFirst, longWindow);
        IndicatorState state = shortMa > longMa ? IndicatorState.ABOVE
            : shortMa < longMa ? IndicatorState.BELOW
            : previous;

        StrategySignal signal = null;
        if (previous == IndicatorState.BELOW && state == IndicatorState.ABOVE) {
            signal = StrategySignal.BUY;
        } else if (previous == IndicatorState.ABOVE && state == IndicatorState.BELOW) {
            signal = StrategySignal.SELL;
        }

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("shortMa", Indicators.scale(shortMa));
        values.put("longMa", Indicators.scale(longMa));
        values.put("price", pricesOldestFirst.get(pricesOldestFirst.size() - 1));

        String averages = shortWindow + "-point average " + Indicators.money(shortMa)
            + (signal == null ? " is " + (state == IndicatorState.BELOW ? "below" : "at or above") + " the "
                : " crossed " + (signal == StrategySignal.BUY ? "above" : "below") + " the ")
            + longWindow + "-point average " + Indicators.money(longMa) + ".";
        return new IndicatorResult(state, signal, values, signal == null ? "No crossover: " + averages : averages);
    }
}
