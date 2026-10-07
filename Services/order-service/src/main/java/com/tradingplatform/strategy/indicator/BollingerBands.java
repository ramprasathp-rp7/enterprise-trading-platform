package com.tradingplatform.strategy.indicator;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategySignal;

/**
 * Bollinger bands: a moving average of the last {@code window} prices with bands k standard
 * deviations above and below it. State is where the newest price sits: INSIDE, ABOVE_UPPER or
 * BELOW_LOWER. A trigger is the price leaving the bands: into BELOW_LOWER is BUY (unusually low),
 * into ABOVE_UPPER is SELL (unusually high). Staying outside does not fire again, and the first
 * evaluation only records the state.
 */
public final class BollingerBands {

    private BollingerBands() {
    }

    public static IndicatorResult evaluate(List<BigDecimal> pricesOldestFirst, int window, double k,
                                           IndicatorState previous) {
        if (window < 2 || k <= 0) {
            throw new IllegalArgumentException("Need window >= 2 and k > 0");
        }
        if (pricesOldestFirst.size() < window) {
            return IndicatorResult.insufficient(pricesOldestFirst.size(), window);
        }

        double price = pricesOldestFirst.get(pricesOldestFirst.size() - 1).doubleValue();
        double middle = Indicators.sma(pricesOldestFirst, window);
        double width = k * Indicators.stdDev(pricesOldestFirst, window);
        double upper = middle + width;
        double lower = middle - width;

        IndicatorState state = price > upper ? IndicatorState.ABOVE_UPPER
            : price < lower ? IndicatorState.BELOW_LOWER
            : IndicatorState.INSIDE;

        StrategySignal signal = null;
        if (previous != null && previous != state) {
            if (state == IndicatorState.BELOW_LOWER) {
                signal = StrategySignal.BUY;
            } else if (state == IndicatorState.ABOVE_UPPER) {
                signal = StrategySignal.SELL;
            }
        }

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("price", Indicators.scale(price));
        values.put("middle", Indicators.scale(middle));
        values.put("upper", Indicators.scale(upper));
        values.put("lower", Indicators.scale(lower));

        String bands = "the " + window + "-point bands " + Indicators.money(lower) + " – " + Indicators.money(upper);
        String reason = switch (state) {
            case BELOW_LOWER -> (signal != null ? "Price " + Indicators.money(price) + " fell below " : "Price is still below ") + bands + ".";
            case ABOVE_UPPER -> (signal != null ? "Price " + Indicators.money(price) + " rose above " : "Price is still above ") + bands + ".";
            default -> "Price " + Indicators.money(price) + " is inside " + bands + ".";
        };
        return new IndicatorResult(state, signal, values, reason);
    }
}
