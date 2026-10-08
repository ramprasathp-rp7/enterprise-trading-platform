package com.tradingplatform.advice.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SignalEngine.
 *
 * Execution paths:
 * - signal generated with reasoning (BUY, SELL, HOLD; position rules)
 * - no signal when data is insufficient (short history, no price)
 */
@DisplayName("SignalEngine Tests")
class SignalEngineTest {

    @Test
    @DisplayName("Signal generated with reasoning: BUY in an established uptrend with a healthy RSI")
    void testBuyWithReasons() {
        SignalEngine.Evaluation e = SignalEngine.evaluate(uptrendThenSteady(), null, null);

        assertEquals(Signal.BUY, e.signal());
        assertTrue(e.hasSignal());
        assertNotNull(e.confidence());
        assertTrue(e.confidence() >= 60 && e.confidence() <= 100);
        assertTrue(e.summary().startsWith("Signals favour buying: an uptrend"), e.summary());
        assertReason(e, "Trend", Reason.Impact.POSITIVE, "Uptrend");
        assertReason(e, "Price vs 20-day average", Reason.Impact.POSITIVE, "above its 20-day average");
        assertEquals(Reason.Impact.NEUTRAL, reason(e, "RSI(14)").impact());
        assertNotNull(e.indicators().sma20());
        assertNotNull(e.indicators().sma50());
        assertNotNull(e.indicators().rsi14());
        assertEquals(60, e.indicators().closes());
    }

    @Test
    @DisplayName("Signal generated with reasoning: SELL in an established downtrend")
    void testSellWithReasons() {
        SignalEngine.Evaluation e = SignalEngine.evaluate(downtrendThenSteady(), null, null);

        assertEquals(Signal.SELL, e.signal());
        assertTrue(e.summary().startsWith("Signals favour avoiding for now: a downtrend"), e.summary());
        assertReason(e, "Trend", Reason.Impact.NEGATIVE, "Downtrend");
        assertReason(e, "Price vs 20-day average", Reason.Impact.NEGATIVE, "below its 20-day average");
    }

    @Test
    @DisplayName("Signal generated with reasoning: HOLD when the factors are mixed")
    void testHoldWhenMixed() {
        List<BigDecimal> flat = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            flat.add(bd(100 + (i % 2 == 0 ? 1 : -1)));
        }

        SignalEngine.Evaluation e = SignalEngine.evaluate(flat, null, new SignalEngine.Holding(10, bd(100)));

        assertEquals(Signal.HOLD, e.signal());
        assertEquals("Mixed signals: no strong case to buy more or sell; hold.", e.summary());
        assertReason(e, "Trend", Reason.Impact.NEUTRAL, "No clear trend");
        assertReason(e, "Your position", Reason.Impact.NEUTRAL, "You hold 10");
    }

    @Test
    @DisplayName("Flags overbought RSI and suggests taking profit on a large gain")
    void testTakeProfit() {
        List<BigDecimal> rising = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rising.add(bd(100 + i * 2));
        }

        SignalEngine.Evaluation e = SignalEngine.evaluate(rising, null, new SignalEngine.Holding(5, bd(120)));

        assertReason(e, "RSI(14)", Reason.Impact.NEGATIVE, "Overbought");
        assertReason(e, "Take profit", Reason.Impact.NEGATIVE, "consider taking some profit");
        assertNotEquals(Signal.BUY, e.signal());
        assertTrue(e.indicators().unrealisedPercent().doubleValue() > 25);
    }

    @Test
    @DisplayName("Flags oversold RSI after a sharp fall, and suggests cutting losses in a falling trend")
    void testOversoldAndCutLosses() {
        List<BigDecimal> falling = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            falling.add(bd(200 - i * 2));
        }

        SignalEngine.Evaluation e = SignalEngine.evaluate(falling, null, new SignalEngine.Holding(5, bd(160)));

        assertReason(e, "RSI(14)", Reason.Impact.POSITIVE, "Oversold");
        assertReason(e, "Cut losses", Reason.Impact.NEGATIVE, "trend is still falling");
    }

    @Test
    @DisplayName("Uses the live price when one is given")
    void testLivePrice() {
        SignalEngine.Evaluation e = SignalEngine.evaluate(uptrendThenSteady(), bd(150.25), null);

        assertEquals(0, bd(150.25).compareTo(e.indicators().price()));
    }

    @Test
    @DisplayName("No signal when data is insufficient: short price history")
    void testInsufficientHistory() {
        List<BigDecimal> shortHistory = uptrendThenSteady().subList(0, 12);

        SignalEngine.Evaluation e = SignalEngine.evaluate(shortHistory, bd(120), null);

        assertNull(e.signal());
        assertNull(e.confidence());
        assertFalse(e.hasSignal());
        assertEquals("Only 12 days of price history; at least 50 are needed for a reliable signal.", e.summary());
        assertEquals(12, e.indicators().closes());
        assertNull(e.indicators().rsi14());
    }

    @Test
    @DisplayName("No signal when data is insufficient: no price at all")
    void testNoPrice() {
        SignalEngine.Evaluation e = SignalEngine.evaluate(List.of(), null, null);

        assertNull(e.signal());
        assertEquals("No price is available for this instrument yet.", e.summary());
        assertNull(e.indicators().price());
    }

    @Test
    @DisplayName("RSI is 50 with no movement, 100 with only gains")
    void testRsiEdges() {
        List<BigDecimal> flat = new ArrayList<>();
        List<BigDecimal> up = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            flat.add(bd(100));
            up.add(bd(100 + i));
        }
        assertEquals(50, SignalEngine.rsi(flat, 14));
        assertEquals(100, SignalEngine.rsi(up, 14));
    }

    // ----- fixtures -----

    /** 40 days rising 100 -> 140, then 20 days edging up with daily swings (RSI around the middle) */
    private static List<BigDecimal> uptrendThenSteady() {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            closes.add(bd(100 + i));
        }
        for (int i = 0; i < 20; i++) {
            closes.add(bd(140 + i * 0.3 + (i % 2 == 0 ? 1.5 : -1.5)));
        }
        return closes;
    }

    /** The mirror image: 40 days falling 140 -> 100, then 20 days edging down with daily swings */
    private static List<BigDecimal> downtrendThenSteady() {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            closes.add(bd(140 - i));
        }
        for (int i = 0; i < 20; i++) {
            closes.add(bd(100 - i * 0.3 + (i % 2 == 0 ? 1.5 : -1.5)));
        }
        return closes;
    }

    private static Reason reason(SignalEngine.Evaluation e, String factor) {
        return e.reasons().stream().filter(r -> r.factor().equals(factor)).findFirst()
            .orElseThrow(() -> new AssertionError("No reason for " + factor + " in " + e.reasons()));
    }

    private static void assertReason(SignalEngine.Evaluation e, String factor, Reason.Impact impact, String text) {
        Reason r = reason(e, factor);
        assertEquals(impact, r.impact(), r.detail());
        assertTrue(r.detail().contains(text), r.detail());
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
