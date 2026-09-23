package com.tradingplatform.executor;

import java.math.BigDecimal;
import com.tradingplatform.enums.OrderStatus;

/**
 * Result of order execution: contains final status, executed price if filled,
 * reject reason if rejected, and duplicate flag.
 */
public record OrderExecutionResult(
    OrderStatus finalStatus,
    BigDecimal executedPrice,  // null if rejected or not executed
    String rejectReason,        // null if filled successfully
    boolean isDuplicate         // true if order was already processed
) {}
