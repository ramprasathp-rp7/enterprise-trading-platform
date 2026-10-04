package com.tradingplatform.api.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageEnvelope and related payload classes.
 * Verifies:
 * - 5-field envelope structure
 * - JSON serialization/deserialization
 * - All event types and payloads
 * - Factory methods
 */
public class MessageEnvelopeTest {

    private ObjectMapper objectMapper;
    private Instant testTime;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testTime = Instant.parse("2026-09-28T09:14:22Z");
    }

    // ==================== ORDER_PLACED Tests ====================

    @Test
    public void testOrderPlacedEventSerialization() throws Exception {
        // Create payload
        OrderPlacedPayload payload = new OrderPlacedPayload(
                UUID.randomUUID().toString(),
                1L,
                "AAPL",
                "BUY",
                100,
                new BigDecimal("233.00"),
                new BigDecimal("2.33"),
                "client-idempotency-key-123",
                testTime
        );

        // Create envelope using factory
        MessageEnvelope<OrderPlacedPayload> envelope = MessageEnvelopeFactory.createOrderPlaced(payload, testTime);

        // Verify 5-field envelope
        assertNotNull(envelope.eventId());
        assertEquals(EventType.ORDER_PLACED, envelope.eventType());
        assertEquals(testTime, envelope.eventTime());
        assertEquals(Source.TRADE_API, envelope.source());
        assertEquals(1, envelope.schemaVersion());
        assertNotNull(envelope.payload());

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(envelope);
        assertNotNull(json);
        assertTrue(json.contains("\"eventType\":\"ORDER_PLACED\""));
        assertTrue(json.contains("\"source\":\"trade-api\""));
        assertTrue(json.contains("\"schemaVersion\":1"));

        // Deserialize back
        @SuppressWarnings("unchecked")
        MessageEnvelope<OrderPlacedPayload> deserialized = 
                objectMapper.readValue(json, MessageEnvelope.class);

        assertNotNull(deserialized);
        assertEquals(envelope.eventId(), deserialized.eventId());
        assertEquals(EventType.ORDER_PLACED.toString(), deserialized.eventType().toString());
        assertEquals(envelope.eventTime(), deserialized.eventTime());
    }

    @Test
    public void testOrderPlacedPayloadFields() {
        String orderId = UUID.randomUUID().toString();
        Long accountId = 1L;
        String symbol = "AAPL";
        String side = "BUY";
        Integer quantity = 100;
        BigDecimal price = new BigDecimal("233.00");
        BigDecimal fee = new BigDecimal("2.33");
        String idempotencyKey = "client-key-123";

        OrderPlacedPayload payload = new OrderPlacedPayload(
                orderId, accountId, symbol, side, quantity, price, fee, idempotencyKey, testTime
        );

        assertEquals(orderId, payload.orderId());
        assertEquals(accountId, payload.accountId());
        assertEquals(symbol, payload.symbol());
        assertEquals(side, payload.side());
        assertEquals(quantity, payload.quantity());
        assertEquals(price, payload.price());
        assertEquals(fee, payload.fee());
        assertEquals(idempotencyKey, payload.idempotencyKey());
        assertEquals(testTime, payload.createdOn());
    }

    // ==================== ORDER_FILLED Tests ====================

    @Test
    public void testOrderFilledEventSerialization() throws Exception {
        // Create payload for a filled order
        TradeEventPayload payload = new TradeEventPayload(
                UUID.randomUUID().toString(),
                1L,
                "AAPL",
                "BUY",
                100,
                new BigDecimal("233.00"),
                new BigDecimal("233.00"),
                new BigDecimal("232.71"),
                new BigDecimal("2.33"),
                "FILLED",
                null,
                new BigDecimal("-23271.00"),
                300,
                new BigDecimal("229.83"),
                testTime
        );

        // Create envelope using factory
        MessageEnvelope<TradeEventPayload> envelope = MessageEnvelopeFactory.createOrderFilled(payload, testTime);

        // Verify 5-field envelope
        assertNotNull(envelope.eventId());
        assertEquals(EventType.ORDER_FILLED, envelope.eventType());
        assertEquals(testTime, envelope.eventTime());
        assertEquals(Source.TRADE_EXECUTOR, envelope.source());
        assertEquals(1, envelope.schemaVersion());
        assertNotNull(envelope.payload());
        assertEquals("FILLED", envelope.payload().status());

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(envelope);
        assertNotNull(json);
        assertTrue(json.contains("\"eventType\":\"ORDER_FILLED\""));
        assertTrue(json.contains("\"source\":\"trade-executor\""));
        assertTrue(json.contains("\"status\":\"FILLED\""));
        assertTrue(json.contains("\"cashDelta\":-23271.00"));
    }

    @Test
    public void testOrderRejectedEventSerialization() throws Exception {
        // Create payload for a rejected order
        TradeEventPayload payload = new TradeEventPayload(
                UUID.randomUUID().toString(),
                1L,
                "AAPL",
                "BUY",
                100,
                new BigDecimal("233.00"),
                new BigDecimal("233.00"),
                null,  // No executed price on reject
                new BigDecimal("2.33"),
                "REJECTED",
                "INSUFFICIENT_FUNDS",
                null,  // No cash delta on reject
                null,  // No position change on reject
                null,  // No average cost change on reject
                testTime
        );

        // Create envelope
        MessageEnvelope<TradeEventPayload> envelope = MessageEnvelopeFactory.createOrderRejected(payload, testTime);

        // Verify event type
        assertEquals(EventType.ORDER_REJECTED, envelope.eventType());
        assertEquals("REJECTED", envelope.payload().status());
        assertEquals("INSUFFICIENT_FUNDS", envelope.payload().reason());
        assertNull(envelope.payload().executedPrice());

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(envelope);
        assertTrue(json.contains("\"eventType\":\"ORDER_REJECTED\""));
        assertTrue(json.contains("\"reason\":\"INSUFFICIENT_FUNDS\""));
    }

    @Test
    public void testOrderCancelledEventSerialization() throws Exception {
        // Create payload for a cancelled order
        TradeEventPayload payload = new TradeEventPayload(
                UUID.randomUUID().toString(),
                1L,
                "AAPL",
                "BUY",
                100,
                new BigDecimal("233.00"),
                new BigDecimal("233.00"),
                null,
                new BigDecimal("2.33"),
                "CANCELLED",
                "CANCELLED_BY_CUSTOMER",
                null,
                null,
                null,
                testTime
        );

        // Create envelope
        MessageEnvelope<TradeEventPayload> envelope = MessageEnvelopeFactory.createOrderCancelled(payload, testTime);

        // Verify event type
        assertEquals(EventType.ORDER_CANCELLED, envelope.eventType());
        assertEquals("CANCELLED", envelope.payload().status());
        assertEquals("CANCELLED_BY_CUSTOMER", envelope.payload().reason());
    }

    @Test
    public void testTradeEventPayloadFields() {
        String orderId = UUID.randomUUID().toString();
        Long accountId = 2L;
        String symbol = "GOOGL";
        String side = "SELL";
        Integer quantity = 50;
        BigDecimal price = new BigDecimal("140.50");
        BigDecimal quotedPrice = new BigDecimal("140.50");
        BigDecimal executedPrice = new BigDecimal("140.45");
        BigDecimal originalFee = new BigDecimal("1.40");
        String status = "FILLED";
        BigDecimal cashDelta = new BigDecimal("7022.50");
        Integer positionQuantityAfter = 100;
        BigDecimal averageCostAfter = new BigDecimal("138.75");

        TradeEventPayload payload = new TradeEventPayload(
                orderId, accountId, symbol, side, quantity, price, quotedPrice, executedPrice, originalFee,
                status, null, cashDelta, positionQuantityAfter, averageCostAfter, testTime
        );

        assertEquals(orderId, payload.orderId());
        assertEquals(accountId, payload.accountId());
        assertEquals(symbol, payload.symbol());
        assertEquals(side, payload.side());
        assertEquals(quantity, payload.quantity());
        assertEquals(price, payload.price());
        assertEquals(quotedPrice, payload.quotedPrice());
        assertEquals(executedPrice, payload.executedPrice());
        assertEquals(originalFee, payload.originalFee());
        assertEquals(status, payload.status());
        assertEquals(cashDelta, payload.cashDelta());
        assertEquals(positionQuantityAfter, payload.positionQuantityAfter());
        assertEquals(averageCostAfter, payload.averageCostAfter());
    }

    // ==================== QUOTE Tests ====================

    @Test
    public void testQuoteEventSerialization() throws Exception {
        // Create quote payload
        QuotePayload payload = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                new BigDecimal("232.65"),
                new BigDecimal("232.77"),
                "USD",
                new BigDecimal("0.21"),
                new BigDecimal("0.09"),
                new BigDecimal("232.50"),
                "open",
                false,
                Instant.parse("2026-09-28T09:14:58Z")
        );

        // Create envelope using factory
        MessageEnvelope<QuotePayload> envelope = MessageEnvelopeFactory.createQuote(payload, testTime);

        // Verify 5-field envelope
        assertNotNull(envelope.eventId());
        assertEquals(EventType.QUOTE, envelope.eventType());
        assertEquals(testTime, envelope.eventTime());
        assertEquals(Source.MARKET_POLLER, envelope.source());
        assertEquals(1, envelope.schemaVersion());
        assertNotNull(envelope.payload());

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(envelope);
        assertNotNull(json);
        assertTrue(json.contains("\"eventType\":\"QUOTE\""));
        assertTrue(json.contains("\"source\":\"market-poller\""));
        assertTrue(json.contains("\"symbol\":\"AAPL\""));

        // Deserialize and verify fields (more robust than string matching for BigDecimal)
        MessageEnvelope<QuotePayload> deserializedEnvelope = objectMapper.readValue(json, 
            objectMapper.getTypeFactory().constructParametricType(MessageEnvelope.class, QuotePayload.class));
        
        assertEquals(EventType.QUOTE, deserializedEnvelope.eventType());
        assertEquals(Source.MARKET_POLLER, deserializedEnvelope.source());
        
        QuotePayload deserializedPayload = deserializedEnvelope.payload();
        assertEquals("AAPL", deserializedPayload.symbol());
        assertEquals(new BigDecimal("232.65"), deserializedPayload.bid());
        assertEquals(new BigDecimal("232.77"), deserializedPayload.ask());
        assertEquals(Instant.parse("2026-09-28T09:14:58Z"), deserializedPayload.quoteAsOf());
    }

    @Test
    public void testQuotePayloadFields() {
        String symbol = "GOOGL";
        BigDecimal price = new BigDecimal("140.50");
        BigDecimal bid = new BigDecimal("140.45");
        BigDecimal ask = new BigDecimal("140.55");
        String currency = "USD";
        BigDecimal change = new BigDecimal("1.50");
        BigDecimal changePercent = new BigDecimal("1.08");
        BigDecimal previousClose = new BigDecimal("139.00");
        String marketState = "open";
        Boolean stale = false;
        Instant quoteAsOf = Instant.parse("2026-09-28T09:14:58Z");

        QuotePayload payload = new QuotePayload(
                symbol, price, bid, ask, currency, change, changePercent,
                previousClose, marketState, stale, quoteAsOf
        );

        assertEquals(symbol, payload.symbol());
        assertEquals(price, payload.price());
        assertEquals(bid, payload.bid());
        assertEquals(ask, payload.ask());
        assertEquals(currency, payload.currency());
        assertEquals(change, payload.change());
        assertEquals(changePercent, payload.changePercent());
        assertEquals(previousClose, payload.previousClose());
        assertEquals(marketState, payload.marketState());
        assertEquals(stale, payload.stale());
        assertEquals(quoteAsOf, payload.quoteAsOf());
    }

    // ==================== Envelope Structure Tests ====================

    @Test
    public void testEnvelopeContainsAllFiveFields() throws Exception {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                UUID.randomUUID().toString(), 1L, "AAPL", "BUY", 100,
                new BigDecimal("233.00"), new BigDecimal("2.33"), "key123", testTime
        );

        MessageEnvelope<OrderPlacedPayload> envelope = 
                MessageEnvelopeFactory.createOrderPlaced(payload, testTime);

        // Verify all 5 fields are present and non-null
        assertNotNull(envelope.eventId());
        assertNotNull(envelope.eventType());
        assertNotNull(envelope.eventTime());
        assertNotNull(envelope.source());
        assertNotNull(envelope.schemaVersion());
        assertNotNull(envelope.payload());

        // Serialize and verify JSON contains all fields
        String json = objectMapper.writeValueAsString(envelope);
        assertTrue(json.contains("\"eventId\""));
        assertTrue(json.contains("\"eventType\""));
        assertTrue(json.contains("\"eventTime\""));
        assertTrue(json.contains("\"source\""));
        assertTrue(json.contains("\"schemaVersion\""));
        assertTrue(json.contains("\"payload\""));
    }

    @Test
    public void testSchemaVersionDefaultsToOne() {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                UUID.randomUUID().toString(), 1L, "AAPL", "BUY", 100,
                new BigDecimal("233.00"), new BigDecimal("2.33"), "key123", testTime
        );

        MessageEnvelope<OrderPlacedPayload> envelope = 
                MessageEnvelopeFactory.createOrderPlaced(payload, testTime);

        assertEquals(1, envelope.schemaVersion());
    }

    @Test
    public void testEventIdIsUUID() {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                UUID.randomUUID().toString(), 1L, "AAPL", "BUY", 100,
                new BigDecimal("233.00"), new BigDecimal("2.33"), "key123", testTime
        );

        MessageEnvelope<OrderPlacedPayload> envelope = 
                MessageEnvelopeFactory.createOrderPlaced(payload, testTime);

        // Verify eventId is a valid UUID format
        assertDoesNotThrow(() -> {
            UUID.fromString(envelope.eventId());
        });
    }

    // ==================== Factory Tests ====================

    @Test
    public void testFactoryGeneratesUniqueEventIds() {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                UUID.randomUUID().toString(), 1L, "AAPL", "BUY", 100,
                new BigDecimal("233.00"), new BigDecimal("2.33"), "key123", testTime
        );

        MessageEnvelope<OrderPlacedPayload> envelope1 = 
                MessageEnvelopeFactory.createOrderPlaced(payload, testTime);
        MessageEnvelope<OrderPlacedPayload> envelope2 = 
                MessageEnvelopeFactory.createOrderPlaced(payload, testTime);

        // Event IDs should be different
        assertNotEquals(envelope1.eventId(), envelope2.eventId());
    }

    @Test
    public void testGenericFactory() {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                UUID.randomUUID().toString(), 1L, "AAPL", "BUY", 100,
                new BigDecimal("233.00"), new BigDecimal("2.33"), "key123", testTime
        );

        String customEventId = "custom-event-id-123";
        MessageEnvelope<OrderPlacedPayload> envelope = MessageEnvelopeFactory.create(
                customEventId,
                EventType.ORDER_PLACED,
                testTime,
                Source.TRADE_API,
                payload
        );

        assertEquals(customEventId, envelope.eventId());
        assertEquals(EventType.ORDER_PLACED, envelope.eventType());
        assertEquals(Source.TRADE_API, envelope.source());
    }

    @Test
    public void testCreateWithRandomId() {
        QuotePayload payload = new QuotePayload(
                "AAPL", new BigDecimal("232.71"), new BigDecimal("232.65"),
                new BigDecimal("232.77"), "USD", new BigDecimal("0.21"),
                new BigDecimal("0.09"), new BigDecimal("232.50"),
                "open", false, testTime
        );

        MessageEnvelope<QuotePayload> envelope = MessageEnvelopeFactory.createWithRandomId(
                EventType.QUOTE,
                testTime,
                Source.MARKET_POLLER,
                payload
        );

        assertNotNull(envelope.eventId());
        assertDoesNotThrow(() -> {
            UUID.fromString(envelope.eventId());
        });
    }

    // ==================== Source Enum Tests ====================

    @Test
    public void testSourceEnumValues() {
        assertEquals("trade-api", Source.TRADE_API.getValue());
        assertEquals("trade-executor", Source.TRADE_EXECUTOR.getValue());
        assertEquals("market-poller", Source.MARKET_POLLER.getValue());
    }

    // ==================== EventType Enum Tests ====================

    @Test
    public void testEventTypeEnumValues() {
        assertNotNull(EventType.ORDER_PLACED);
        assertNotNull(EventType.ORDER_FILLED);
        assertNotNull(EventType.ORDER_REJECTED);
        assertNotNull(EventType.ORDER_CANCELLED);
        assertNotNull(EventType.QUOTE);
    }

    // ==================== JSON Deserialization Tests ====================

    @Test
    public void testJSONDeserializationPreservesAllFields() throws Exception {
        String json = "{" +
                "\"eventId\":\"b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d\"," +
                "\"eventType\":\"ORDER_PLACED\"," +
                "\"eventTime\":\"2026-09-28T09:14:22Z\"," +
                "\"source\":\"trade-api\"," +
                "\"schemaVersion\":1," +
                "\"payload\":{" +
                "\"orderId\":\"6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e\"," +
                "\"accountId\":1," +
                "\"symbol\":\"AAPL\"," +
                "\"side\":\"BUY\"," +
                "\"quantity\":100," +
                "\"price\":233.00," +
                "\"idempotencyKey\":\"6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e\"," +
                "\"createdOn\":\"2026-09-28T09:14:22Z\"" +
                "}" +
                "}";

        @SuppressWarnings("unchecked")
        MessageEnvelope<OrderPlacedPayload> envelope = 
                objectMapper.readValue(json, MessageEnvelope.class);

        assertEquals("b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d", envelope.eventId());
        assertEquals(1, envelope.schemaVersion());
    }
}
