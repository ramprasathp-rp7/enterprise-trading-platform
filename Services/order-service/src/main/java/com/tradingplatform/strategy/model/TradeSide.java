package com.tradingplatform.strategy.model;

/** Which signals a strategy acts on */
public enum TradeSide {
    BUY_ONLY,
    SELL_ONLY,
    BOTH;

    public boolean allows(StrategySignal signal) {
        return this == BOTH
            || (this == BUY_ONLY && signal == StrategySignal.BUY)
            || (this == SELL_ONLY && signal == StrategySignal.SELL);
    }
}
