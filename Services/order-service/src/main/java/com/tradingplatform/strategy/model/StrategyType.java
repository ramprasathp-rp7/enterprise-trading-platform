package com.tradingplatform.strategy.model;

/** Which trigger a strategy uses */
public enum StrategyType {
    /** Short moving average crossing the long one */
    MA_CROSSOVER,
    /** Price leaving the Bollinger bands */
    BOLLINGER
}
