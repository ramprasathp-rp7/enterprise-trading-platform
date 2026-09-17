package com.tradingplatform.api.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for ORDER_FILLED, ORDER_REJECTED, and ORDER_CANCELLED events on 'trade-events' topic.
 * Represents the terminal outcome of an order.
 *
 * @param orderId The order identifier.
 * @param accountId The numeric account key, also the message key (as a string).
 * @param symbol Instrument symbol.
 * @param side Side of the order: BUY or SELL.
 * @param quantity Quantity in the order (entire quantity for fills).
 * @param price The limit price from the order.
 * @param executedPrice The Fauxnance quote the fill was priced at (null on reject/cancel).
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
    
    @JsonProperty("executedPrice")
    BigDecimal executedPrice,
    
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
    Instant executedOn
) {}
