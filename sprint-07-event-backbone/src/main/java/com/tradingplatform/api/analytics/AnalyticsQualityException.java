package com.tradingplatform.api.analytics;

import java.util.UUID;

/**
 * Exception thrown when analytics quality validation fails.
 * Contains reason, reason code, and source order ID for dead-lettering.
 */
public class AnalyticsQualityException extends RuntimeException {
    private final String reasonCode;
    private final UUID sourceOrderId;

    public AnalyticsQualityException(String reason, String reasonCode, UUID sourceOrderId) {
        super(reason);
        this.reasonCode = reasonCode;
        this.sourceOrderId = sourceOrderId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public UUID getSourceOrderId() {
        return sourceOrderId;
    }
}
