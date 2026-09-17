package com.tradingplatform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Utility factory for creating MessageEnvelope instances.
 * Provides builder-style convenience methods for creating properly-formed events.
 * Always sets schemaVersion to 1.
 */
public class MessageEnvelopeFactory {

    /**
     * Creates an ORDER_PLACED event envelope.
     *
     * @param payload the OrderPlacedPayload
     * @param eventTime when the order was created
     * @return a new MessageEnvelope for the orders topic
     */
    public static MessageEnvelope<OrderPlacedPayload> createOrderPlaced(
            OrderPlacedPayload payload, Instant eventTime) {
        return new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_PLACED,
                eventTime,
                Source.TRADE_API,
                payload
        );
    }

    /**
     * Creates an ORDER_FILLED event envelope.
     *
     * @param payload the TradeEventPayload
     * @param eventTime when the order was filled
     * @return a new MessageEnvelope for the trade-events topic
     */
    public static MessageEnvelope<TradeEventPayload> createOrderFilled(
            TradeEventPayload payload, Instant eventTime) {
        return new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_FILLED,
                eventTime,
                Source.TRADE_EXECUTOR,
                payload
        );
    }

    /**
     * Creates an ORDER_REJECTED event envelope.
     *
     * @param payload the TradeEventPayload
     * @param eventTime when the order was rejected
     * @return a new MessageEnvelope for the trade-events topic
     */
    public static MessageEnvelope<TradeEventPayload> createOrderRejected(
            TradeEventPayload payload, Instant eventTime) {
        return new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_REJECTED,
                eventTime,
                Source.TRADE_EXECUTOR,
                payload
        );
    }

    /**
     * Creates an ORDER_CANCELLED event envelope.
     *
     * @param payload the TradeEventPayload
     * @param eventTime when the order was cancelled
     * @return a new MessageEnvelope for the trade-events topic
     */
    public static MessageEnvelope<TradeEventPayload> createOrderCancelled(
            TradeEventPayload payload, Instant eventTime) {
        return new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_CANCELLED,
                eventTime,
                Source.TRADE_EXECUTOR,
                payload
        );
    }

    /**
     * Creates a QUOTE event envelope.
     *
     * @param payload the QuotePayload
     * @param eventTime when the quote was polled (not quoteAsOf)
     * @return a new MessageEnvelope for the market-data topic
     */
    public static MessageEnvelope<QuotePayload> createQuote(
            QuotePayload payload, Instant eventTime) {
        return new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.QUOTE,
                eventTime,
                Source.MARKET_POLLER,
                payload
        );
    }

    /**
     * Generic factory for custom event types.
     *
     * @param <T> the payload type
     * @param eventId unique identifier for the event (typically UUID)
     * @param eventType the type of event
     * @param eventTime when the event occurred
     * @param source the producing component
     * @param payload the event-specific payload
     * @return a new MessageEnvelope
     */
    public static <T> MessageEnvelope<T> create(String eventId, EventType eventType,
                                                 Instant eventTime, Source source, T payload) {
        return new MessageEnvelope<>(eventId, eventType, eventTime, source, payload);
    }

    /**
     * Generic factory that generates a random UUID for the event ID.
     *
     * @param <T> the payload type
     * @param eventType the type of event
     * @param eventTime when the event occurred
     * @param source the producing component
     * @param payload the event-specific payload
     * @return a new MessageEnvelope with a generated UUID
     */
    public static <T> MessageEnvelope<T> createWithRandomId(EventType eventType,
                                                             Instant eventTime, Source source,
                                                             T payload) {
        return new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                eventTime,
                source,
                payload
        );
    }
}
