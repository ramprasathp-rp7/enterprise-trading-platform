package com.tradingplatform.strategy.model;

/** What happened when a strategy triggered */
public enum ExecutionOutcome {
    /** Order accepted by the Trade API */
    PLACED,
    /** Trade API rejected the order or could not be reached */
    FAILED,
    /** Strategy was disabled before the order was sent */
    SKIPPED_DISABLED,
    /** Max position or max daily spend would be exceeded */
    SKIPPED_LIMIT,
    /** A sell signal with not enough of the instrument held */
    SKIPPED_NO_POSITION
}
