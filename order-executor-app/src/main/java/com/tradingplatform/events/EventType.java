package com.tradingplatform.events;

/**
 * Event type enumeration for all Kafka message payloads.
 * Discriminates the payload structure on orders, trade-events, and market-data topics.
 */
public enum EventType {
    // orders topic
    ORDER_PLACED,

    // trade-events topic
    ORDER_FILLED,
    ORDER_REJECTED,
    ORDER_CANCELLED,

    // market-data topic
    QUOTE
}
