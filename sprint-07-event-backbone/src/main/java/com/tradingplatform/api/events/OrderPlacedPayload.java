package com.tradingplatform.api.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for ORDER_PLACED events on the 'orders' topic.
 * Represents an order accepted by the Trade REST API and awaiting execution.
 *
 * @param orderId UUID, matches orders.id in Postgres.
 * @param accountId The numeric account key, also the message key (as a string).
 * @param symbol Instrument symbol in the Fauxnance scheme.
 * @param side Side of the order: BUY or SELL.
 * @param quantity Quantity ordered, greater than zero.
 * @param price Limit price per unit, two decimal places (quoted price).
 * @param fee Transaction fee calculated at order time (for BUY orders).
 * @param idempotencyKey The client's idempotency key that was accepted.
 * @param createdOn When the order was recorded in the database.
 */
public record OrderPlacedPayload(
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
    
    @JsonProperty("fee")
    BigDecimal fee,
    
    @JsonProperty("idempotencyKey")
    String idempotencyKey,
    
    @JsonProperty("createdOn")
    Instant createdOn
) {}
