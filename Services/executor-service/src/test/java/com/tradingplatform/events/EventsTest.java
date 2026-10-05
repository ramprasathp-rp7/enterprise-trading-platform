package com.tradingplatform.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class EventsTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:15:30Z");

    private TradeEventPayload tradePayload() {
        return new TradeEventPayload("order-1", 42L, "AAPL", "BUY", 10, new BigDecimal("100.00"),
            new BigDecimal("100.00"), new BigDecimal("99.50"), new BigDecimal("1.00"), "FILLED",
            null, new BigDecimal("-995.00"), 10, new BigDecimal("99.50"), NOW);
    }

    @Test
    void factoryCreatesEnvelopesWithCorrectTypeAndSource() {
        OrderPlacedPayload placed = new OrderPlacedPayload("o", 1L, "AAPL", "BUY", 1,
            BigDecimal.ONE, BigDecimal.ZERO, "k", NOW);
        QuotePayload quote = new QuotePayload("AAPL", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
            "USD", null, null, null, "open", false, NOW);
        TradeEventPayload trade = tradePayload();

        assertEnvelope(MessageEnvelopeFactory.createOrderPlaced(placed, NOW), EventType.ORDER_PLACED, Source.TRADE_API, placed);
        assertEnvelope(MessageEnvelopeFactory.createOrderFilled(trade, NOW), EventType.ORDER_FILLED, Source.TRADE_EXECUTOR, trade);
        assertEnvelope(MessageEnvelopeFactory.createOrderRejected(trade, NOW), EventType.ORDER_REJECTED, Source.TRADE_EXECUTOR, trade);
        assertEnvelope(MessageEnvelopeFactory.createOrderCancelled(trade, NOW), EventType.ORDER_CANCELLED, Source.TRADE_EXECUTOR, trade);
        assertEnvelope(MessageEnvelopeFactory.createQuote(quote, NOW), EventType.QUOTE, Source.MARKET_POLLER, quote);
        assertEnvelope(MessageEnvelopeFactory.createWithRandomId(EventType.QUOTE, NOW, Source.MARKET_POLLER, quote),
            EventType.QUOTE, Source.MARKET_POLLER, quote);
    }

    @Test
    void factoryGeneratesDistinctEventIds() {
        TradeEventPayload trade = tradePayload();
        assertNotEquals(
            MessageEnvelopeFactory.createOrderFilled(trade, NOW).eventId(),
            MessageEnvelopeFactory.createOrderFilled(trade, NOW).eventId());
    }

    @Test
    void genericCreateUsesSuppliedEventId() {
        MessageEnvelope<String> env = MessageEnvelopeFactory.create("evt-1", EventType.QUOTE, NOW, Source.MARKET_POLLER, "p");
        assertEquals("evt-1", env.eventId());
        assertEquals(1, env.schemaVersion());
    }

    @Test
    void canonicalConstructorDefaultsMissingSchemaVersion() {
        MessageEnvelope<String> defaulted = new MessageEnvelope<>("e", EventType.QUOTE, NOW, Source.MARKET_POLLER, null, "p");
        MessageEnvelope<String> explicit = new MessageEnvelope<>("e", EventType.QUOTE, NOW, Source.MARKET_POLLER, 2, "p");
        assertEquals(1, defaulted.schemaVersion());
        assertEquals(2, explicit.schemaVersion());
    }

    @Test
    void sourceFromValueResolvesKnownValuesOnly() {
        for (Source s : Source.values()) {
            assertSame(s, Source.fromValue(s.getValue()));
        }
        assertNull(Source.fromValue(null));
        assertThrows(IllegalArgumentException.class, () -> Source.fromValue("unknown"));
    }

    @Test
    void tradeEventEnvelopeRoundTripsThroughJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TradeEventPayload payload = tradePayload();
        String json = mapper.writeValueAsString(MessageEnvelopeFactory.createOrderFilled(payload, NOW));

        MessageEnvelope<TradeEventPayload> parsed = mapper.readValue(json,
            new com.fasterxml.jackson.core.type.TypeReference<MessageEnvelope<TradeEventPayload>>() {});

        assertEquals(EventType.ORDER_FILLED, parsed.eventType());
        assertEquals(Source.TRADE_EXECUTOR, parsed.source());
        assertEquals(payload, parsed.payload());
    }

    private static <T> void assertEnvelope(MessageEnvelope<T> env, EventType type, Source source, T payload) {
        assertNotNull(env.eventId());
        assertEquals(type, env.eventType());
        assertEquals(NOW, env.eventTime());
        assertEquals(source, env.source());
        assertEquals(1, env.schemaVersion());
        assertSame(payload, env.payload());
    }
}
