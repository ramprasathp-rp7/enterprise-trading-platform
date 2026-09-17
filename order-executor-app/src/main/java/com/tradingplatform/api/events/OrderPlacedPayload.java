package com.tradingplatform.api.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderPlacedPayload(
    @JsonProperty("orderId") String orderId,
    @JsonProperty("accountId") Long accountId,
    @JsonProperty("symbol") String symbol,
    @JsonProperty("side") String side,
    @JsonProperty("quantity") Integer quantity,
    @JsonProperty("price") BigDecimal price,
    @JsonProperty("idempotencyKey") String idempotencyKey,
    @JsonProperty("createdOn") Instant createdOn
) {}
