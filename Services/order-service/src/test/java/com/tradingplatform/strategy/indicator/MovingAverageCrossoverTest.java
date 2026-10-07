package com.tradingplatform.strategy.indicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategySignal;

class MovingAverageCrossoverTest {

    private static List<BigDecimal> prices(double... values) {
        return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void buysWhenTheShortAverageCrossesAboveTheLongOne() {
        // short(2) = 104, long(4) = 101.5: short is now above, and was below before
        IndicatorResult result = MovingAverageCrossover.evaluate(prices(100, 98, 103, 105), 2, 4, IndicatorState.BELOW);

        assertEquals(IndicatorState.ABOVE, result.state());
        assertEquals(StrategySignal.BUY, result.signal());
        assertTrue(result.triggered());
        assertEquals(0, new BigDecimal("104").compareTo(result.values().get("shortMa")));
        assertEquals(0, new BigDecimal("101.5").compareTo(result.values().get("longMa")));
        assertTrue(result.reason().startsWith("2-point average 104.00 crossed above the 4-point average 101.50"));
    }

    @Test
    void sellsWhenTheShortAverageCrossesBelowTheLongOne() {
        IndicatorResult result = MovingAverageCrossover.evaluate(prices(105, 103, 98, 96), 2, 4, IndicatorState.ABOVE);

        assertEquals(IndicatorState.BELOW, result.state());
        assertEquals(StrategySignal.SELL, result.signal());
    }

    @Test
    void doesNothingWhileTheShortAverageStaysOnTheSameSide() {
        IndicatorResult result = MovingAverageCrossover.evaluate(prices(100, 101, 102, 103, 104), 2, 4, IndicatorState.ABOVE);

        assertEquals(IndicatorState.ABOVE, result.state());
        assertNull(result.signal());
        assertFalse(result.triggered());
        assertTrue(result.reason().startsWith("No crossover"));
    }

    @Test
    void doesNothingOnAFlatSeries() {
        IndicatorResult result = MovingAverageCrossover.evaluate(prices(100, 100, 100, 100), 2, 4, IndicatorState.BELOW);

        assertEquals(IndicatorState.BELOW, result.state(), "equal averages keep the previous state");
        assertNull(result.signal());
    }

    @Test
    void onlyRecordsTheStateOnTheFirstEvaluation() {
        IndicatorResult result = MovingAverageCrossover.evaluate(prices(100, 98, 103, 105), 2, 4, null);

        assertEquals(IndicatorState.ABOVE, result.state());
        assertNull(result.signal());
    }

    @Test
    void waitsForEnoughPrices() {
        IndicatorResult result = MovingAverageCrossover.evaluate(prices(100, 101, 102), 2, 4, IndicatorState.BELOW);

        assertNull(result.state());
        assertNull(result.signal());
        assertEquals("Waiting for price history: 3 of 4 prices so far.", result.reason());
    }

    @Test
    void rejectsWindowsInTheWrongOrder() {
        assertThrows(IllegalArgumentException.class, () -> MovingAverageCrossover.evaluate(prices(1, 2, 3), 3, 2, null));
    }
}
