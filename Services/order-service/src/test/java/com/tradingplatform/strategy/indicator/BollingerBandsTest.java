package com.tradingplatform.strategy.indicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategySignal;

class BollingerBandsTest {

    private static List<BigDecimal> prices(double... values) {
        return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void computesTheBandsAroundTheAverage() {
        // mean 100, population std dev 2 → bands 96 – 104 for k = 2
        IndicatorResult result = BollingerBands.evaluate(prices(98, 102, 98, 102), 4, 2, IndicatorState.INSIDE);

        assertEquals(0, new BigDecimal("100").compareTo(result.values().get("middle")));
        assertEquals(0, new BigDecimal("104").compareTo(result.values().get("upper")));
        assertEquals(0, new BigDecimal("96").compareTo(result.values().get("lower")));
        assertEquals(IndicatorState.INSIDE, result.state());
        assertNull(result.signal());
    }

    @Test
    void buysWhenThePriceFallsBelowTheLowerBand() {
        IndicatorResult result = BollingerBands.evaluate(prices(100, 100, 100, 100, 100, 90), 6, 2, IndicatorState.INSIDE);

        assertEquals(IndicatorState.BELOW_LOWER, result.state());
        assertEquals(StrategySignal.BUY, result.signal());
        assertTrue(result.reason().startsWith("Price 90.00 fell below the 6-point bands"));
    }

    @Test
    void sellsWhenThePriceRisesAboveTheUpperBand() {
        IndicatorResult result = BollingerBands.evaluate(prices(100, 100, 100, 100, 100, 110), 6, 2, IndicatorState.INSIDE);

        assertEquals(IndicatorState.ABOVE_UPPER, result.state());
        assertEquals(StrategySignal.SELL, result.signal());
    }

    @Test
    void doesNotFireAgainWhileThePriceStaysOutside() {
        IndicatorResult result = BollingerBands.evaluate(prices(100, 100, 100, 100, 100, 90), 6, 2, IndicatorState.BELOW_LOWER);

        assertEquals(IndicatorState.BELOW_LOWER, result.state());
        assertNull(result.signal());
        assertTrue(result.reason().startsWith("Price is still below"));
    }

    @Test
    void doesNothingInsideTheBandsOrOnAFlatSeries() {
        assertNull(BollingerBands.evaluate(prices(100, 101, 99, 100), 4, 2, IndicatorState.INSIDE).signal());
        IndicatorResult flat = BollingerBands.evaluate(prices(100, 100, 100, 100), 4, 2, IndicatorState.BELOW_LOWER);
        assertEquals(IndicatorState.INSIDE, flat.state());
        assertNull(flat.signal(), "moving back inside never trades");
    }

    @Test
    void onlyRecordsTheStateOnTheFirstEvaluation() {
        IndicatorResult result = BollingerBands.evaluate(prices(100, 100, 100, 100, 100, 90), 6, 2, null);

        assertEquals(IndicatorState.BELOW_LOWER, result.state());
        assertNull(result.signal());
    }

    @Test
    void waitsForEnoughPrices() {
        IndicatorResult result = BollingerBands.evaluate(prices(100, 101), 4, 2, IndicatorState.INSIDE);

        assertNull(result.state());
        assertNull(result.signal());
    }
}
