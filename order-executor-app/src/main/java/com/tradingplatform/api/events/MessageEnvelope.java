package com.tradingplatform.api.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record MessageEnvelope<T>(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("eventType") EventType eventType,
    @JsonProperty("eventTime") Instant eventTime,
    @JsonProperty("source") Source source,
    @JsonProperty("schemaVersion") Integer schemaVersion,
    @JsonProperty("payload") T payload
) {
    public MessageEnvelope {
        if (schemaVersion == null) schemaVersion = 1;
    }
    public MessageEnvelope(String eventId, EventType eventType, Instant eventTime, Source source, T payload) {
        this(eventId, eventType, eventTime, source, 1, payload);
    }
}
