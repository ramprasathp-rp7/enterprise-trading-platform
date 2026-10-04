package com.tradingplatform.events;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic message envelope for all Kafka topics.
 * Encapsulates metadata and payload for orders, trade-events, and market-data topics.
 * 
 * The envelope is identical across all topics so that one deserializer and one
 * dead-letter handler cover the platform.
 *
 * @param eventId Unique per message, typically UUID. The idempotency key for consumers.
 * @param eventType Discriminates the payload structure (ORDER_PLACED, ORDER_FILLED, QUOTE).
 * @param eventTime When the producer created the event (RFC 3339 UTC).
 * @param source Producing component: trade-api, trade-executor, market-poller.
 * @param schemaVersion Schema version starting at 1 for safe evolution.
 * @param payload Topic-specific and event-type-specific payload.
 * @param <T> The payload type (OrderPlaced, OrderFilled, Quote, etc.)
 */
public record MessageEnvelope<T>(
    @JsonProperty("eventId")
    String eventId,
    
    @JsonProperty("eventType")
    EventType eventType,
    
    @JsonProperty("eventTime")
    Instant eventTime,
    
    @JsonProperty("source")
    Source source,
    
    @JsonProperty("schemaVersion")
    Integer schemaVersion,
    
    @JsonProperty("payload")
    T payload
) {
    /**
     * Compact constructor for no-arg deserialization by Jackson.
     * Sets default schemaVersion to 1 if not provided.
     */
    public MessageEnvelope {
        if (schemaVersion == null) {
            schemaVersion = 1;
        }
    }

    /**
     * Convenience constructor with 5 parameters.
     * Auto-sets schemaVersion to 1.
     */
    public MessageEnvelope(String eventId, EventType eventType, Instant eventTime,
                          Source source, T payload) {
        this(eventId, eventType, eventTime, source, 1, payload);
    }
}
