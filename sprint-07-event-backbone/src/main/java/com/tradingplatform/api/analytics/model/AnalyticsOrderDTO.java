package com.tradingplatform.api.analytics.model;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for orders extracted from Postgres.
 */
public record AnalyticsOrderDTO(
    UUID orderId,
    Long accountId,
    String accountIdStr,
    String holderName,
    String symbol,
    String side,
    Integer quantity,
    java.math.BigDecimal price,
    java.math.BigDecimal executedPrice,
    String status,
    Instant createdOn
) {}
