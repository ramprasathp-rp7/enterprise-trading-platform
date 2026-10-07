package com.tradingplatform.advice.engine;

/**
 * One factor behind a signal.
 *
 * @param factor short name, e.g. "Trend", "RSI(14)"
 * @param impact whether it points towards buying, selling, or neither
 * @param detail one sentence in plain language, with the numbers behind it
 */
public record Reason(String factor, Impact impact, String detail) {

    public enum Impact {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }
}
