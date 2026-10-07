package com.tradingplatform.events;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for ORDER_FILLED, ORDER_REJECTED, and ORDER_CANCELLED events on 'trade-events' topic.
 * Represents the terminal outcome of an order.
 *
 * @param orderId The order identifier.
 * @param accountId The numeric account key, also the message key (as a string).
 * @param symbol Instrument symbol.
 * @param side Side of the order: BUY or SELL.
 * @param quantity Quantity in the order (entire quantity for fills).
 * @param price The limit price from the order (deprecated, use quotedPrice instead).
 * @param quotedPrice The original quoted price from the order (used for fee recalculation).
 * @param executedPrice The Fauxnance quote the fill was priced at (null on reject/cancel).
 * @param originalFee The fee calculated at order placement time (used to compare against recalculated fee).
 * @param status Terminal order status: FILLED, REJECTED, or CANCELLED.
 * @param reason Reason for rejection or cancellation (null on fill).
 * @param cashDelta Signed change to the cash balance (null on reject).
 * @param positionQuantityAfter Net held quantity after this event (null on reject).
 * @param averageCostAfter Weighted average cost after this event (null on reject).
 * @param executedOn When the order was executed (or rejected/cancelled).
 */
public record TradeEventPayload(
    @JsonProperty("orderId")
    String orderId,
    
    @JsonProperty("accountId")
    Long accountId,
    
    @JsonProperty("symbol")
    String symbol,
    
    @JsonProperty("side")
    String side,
    
    @JsonProperty("quantity")
    Integer quantity,
    
    @JsonProperty("price")
    BigDecimal price,
    
    @JsonProperty("quotedPrice")
    BigDecimal quotedPrice,
    
    @JsonProperty("executedPrice")
    BigDecimal executedPrice,
    
    @JsonProperty("originalFee")
    BigDecimal originalFee,
    
    @JsonProperty("status")
    String status,
    
    @JsonProperty("reason")
    String reason,
    
    @JsonProperty("cashDelta")
    BigDecimal cashDelta,
    
    @JsonProperty("positionQuantityAfter")
    Integer positionQuantityAfter,
    
    @JsonProperty("averageCostAfter")
    BigDecimal averageCostAfter,
    
    @JsonProperty("executedOn")
    Instant executedOn,
    
    @JsonProperty("fillFxRate")
    BigDecimal fillFxRate
) {
    /**
     * Pre-multi-currency shape. {@code fillFxRate} is the instrument-to-account rate fetched by the
     * executor at fill; events without it settle at a rate of 1 (same currency).
     */
    public TradeEventPayload(String orderId, Long accountId, String symbol, String side, Integer quantity,
                             BigDecimal price, BigDecimal quotedPrice, BigDecimal executedPrice,
                             BigDecimal originalFee, String status, String reason, BigDecimal cashDelta,
                             Integer positionQuantityAfter, BigDecimal averageCostAfter, Instant executedOn) {
        this(orderId, accountId, symbol, side, quantity, price, quotedPrice, executedPrice, originalFee, status,
            reason, cashDelta, positionQuantityAfter, averageCostAfter, executedOn, null);
    }
}
