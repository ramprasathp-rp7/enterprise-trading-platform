package com.tradingplatform.strategy.model;

/**
 * Indicator state after an evaluation. A trigger is a change of state:
 * ABOVE / BELOW for moving-average crossover (short vs long average),
 * INSIDE / ABOVE_UPPER / BELOW_LOWER for Bollinger bands (price vs bands).
 */
public enum IndicatorState {
    ABOVE,
    BELOW,
    INSIDE,
    ABOVE_UPPER,
    BELOW_LOWER
}
