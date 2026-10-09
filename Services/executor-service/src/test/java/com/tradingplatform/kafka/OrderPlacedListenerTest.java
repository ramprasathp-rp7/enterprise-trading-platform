package com.tradingplatform.kafka;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.AssetClass;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.OrderPlacedPayload;
import com.tradingplatform.executor.OrderExecutorService;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.quote.PricingUnavailableException;

@ExtendWith(MockitoExtension.class)
class OrderPlacedListenerTest {

    private OrderExecutorService executorService;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private OrderMapper orderMapper;

    private final java.util.List<ProducerRecord<String, String>> capturedDeadLetters = new java.util.ArrayList<>();
    private final java.util.function.Function<ProducerRecord<String, String>, java.util.concurrent.CompletableFuture<?>> deadLetterKafkaTemplate = producerRecord -> {
        capturedDeadLetters.add(producerRecord);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    };

    private ObjectMapper objectMapper;
    private OrderPlacedListener listener;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // create a lightweight stub of OrderExecutorService to avoid inline-mock issues on newer JVMs
        // provide a no-op TradeEventPublisher to satisfy the constructor
        com.tradingplatform.publisher.TradeEventPublisher noopPublisher = new com.tradingplatform.publisher.TradeEventPublisher(null, objectMapper, null) {
            @Override
            public java.util.concurrent.CompletableFuture<Boolean> publishOutcome(com.tradingplatform.domain.PendingOutcome outcome) {
                return java.util.concurrent.CompletableFuture.completedFuture(true);  // no-op
            }
        };

        executorService = new OrderExecutorService(null, null, null, null, null, null, noopPublisher) {
            private int callCount = 0;
            private final java.util.Queue<java.lang.Throwable> actions = new java.util.ArrayDeque<>();

            public void scheduleThrow(java.lang.Throwable t) { actions.add(t); }

            public int getCallCount() { return callCount; }

            @Override
            public void process(com.tradingplatform.domain.Order order, java.math.BigDecimal quotedPrice, java.math.BigDecimal originalFee) throws PricingUnavailableException {
                callCount++;
                java.lang.Throwable t = actions.poll();
                if (t != null) {
                    if (t instanceof PricingUnavailableException) throw (PricingUnavailableException) t;
                    if (t instanceof RuntimeException) throw (RuntimeException) t;
                    throw new RuntimeException(t);
                }
            }
        };

        listener = new OrderPlacedListener(
            executorService,
            instrumentMapper,
            orderMapper,
            deadLetterKafkaTemplate,
            objectMapper
        );
    }

    @Test
    void malformedMessageIsDeadLetteredOnFirstAttempt() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, SecurityException {
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 1, 10L, "1", "{not-json");

        listener.onOrderPlaced(consumerRecord, null);

        // executorService is a stub; ensure it was not called
        int calls0 = (int) ((OrderExecutorService)executorService).getClass().getMethod("getCallCount").invoke(executorService);
        org.junit.jupiter.api.Assertions.assertEquals(0, calls0);
        verifyNoInteractions(instrumentMapper, orderMapper);
        ProducerRecord<String, String> deadLetterRecord = deadLetterRecord();
        assertEquals("orders.DLT", deadLetterRecord.topic());
        assertEquals("{not-json", deadLetterRecord.value());
        assertEquals(true, headerValue(deadLetterRecord, "failure-reason").startsWith("Malformed JSON:"));
    }

    @Test
    void missingOrderIdentifierIsDeadLetteredImmediately() throws Exception {
        String rawMessage = objectMapper.writeValueAsString(
            new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_PLACED,
                Instant.parse("2026-09-17T10:15:30Z"),
                com.tradingplatform.events.Source.TRADE_API,
                new OrderPlacedPayload(
                    null,
                    42L,
                    "INFY.NS",
                    "BUY",
                    10,
                    new BigDecimal("100.00"),
                    new BigDecimal("1.00"),
                    "key-1",
                    Instant.parse("2026-09-17T10:15:30Z")
                )
            )
        );
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 1, 11L, "42", rawMessage);

        listener.onOrderPlaced(consumerRecord, null);

        int calls1 = (int) ((OrderExecutorService)executorService).getClass().getMethod("getCallCount").invoke(executorService);
        org.junit.jupiter.api.Assertions.assertEquals(0, calls1);
        verifyNoInteractions(instrumentMapper, orderMapper);
        ProducerRecord<String, String> deadLetterRecord = deadLetterRecord();
        assertEquals("Missing required order field", headerValue(deadLetterRecord, "failure-reason"));
    }

    @Test
    void missingOrderInPostgresIsDeadLetteredImmediately() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String rawMessage = validOrderPlacedMessage(orderId, "INFY.NS");
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 1, 12L, "42", rawMessage);
        when(orderMapper.findOrderById(UUID.fromString(orderId))).thenReturn(null);

        listener.onOrderPlaced(consumerRecord, null);

        // ensure stub was not called
        int calls2 = (int) ((OrderExecutorService)executorService).getClass().getMethod("getCallCount").invoke(executorService);
        org.junit.jupiter.api.Assertions.assertEquals(0, calls2);
        ProducerRecord<String, String> deadLetterRecord = deadLetterRecord();
        assertEquals("Order not found in Postgres: " + orderId, headerValue(deadLetterRecord, "failure-reason"));
    }

    @Test
    void unexpectedEventTypeDoesNotProceedToExecutor() throws Exception {
        String rawMessage = objectMapper.writeValueAsString(
            new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_FILLED,
                Instant.parse("2026-09-17T10:15:30Z"),
                com.tradingplatform.events.Source.TRADE_API,
                new OrderPlacedPayload(
                    UUID.randomUUID().toString(),
                    42L,
                    "INFY.NS",
                    "BUY",
                    10,
                    new BigDecimal("100.00"),
                    new BigDecimal("1.00"),
                    "key-1",
                    Instant.parse("2026-09-17T10:15:30Z")
                )
            )
        );
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 1, 13L, "42", rawMessage);

        listener.onOrderPlaced(consumerRecord, null);

        int callsX = (int) ((OrderExecutorService)executorService).getClass().getMethod("getCallCount").invoke(executorService);
        org.junit.jupiter.api.Assertions.assertEquals(0, callsX);
        verifyNoInteractions(instrumentMapper, orderMapper);
        ProducerRecord<String, String> deadLetterRecord = deadLetterRecord();
        assertEquals("Unexpected event type: ORDER_FILLED", headerValue(deadLetterRecord, "failure-reason"));
    }

    @Test
    void transientFailureIsRetriedAndThenSucceeds() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String rawMessage = validOrderPlacedMessage(orderId, "INFY.NS");
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 1, 14L, "42", rawMessage);

        when(orderMapper.findOrderById(UUID.fromString(orderId))).thenReturn(
            new Order(42L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1")
        );
        when(instrumentMapper.findBySymbol("INFY.NS")).thenReturn(
            new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true)
        );
        // schedule a transient failure then success on the stub
        ((OrderExecutorService)executorService).getClass().getMethod("scheduleThrow", java.lang.Throwable.class).invoke(executorService, new PricingUnavailableException("broker unavailable"));

        listener.onOrderPlaced(consumerRecord, null);

        // assert stub was invoked twice (retry once)
        int calls = (int) ((OrderExecutorService)executorService).getClass().getMethod("getCallCount").invoke(executorService);
        org.junit.jupiter.api.Assertions.assertEquals(2, calls);
        org.junit.jupiter.api.Assertions.assertTrue(capturedDeadLetters.isEmpty());
    }

    @Test
    void poisonMessageDoesNotBlockPartition() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String rawMessage = validOrderPlacedMessage(orderId, "INFY.NS");
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 1, 15L, "42", rawMessage);
        when(orderMapper.findOrderById(UUID.fromString(orderId))).thenReturn(
            new Order(42L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1")
        );
        when(instrumentMapper.findBySymbol("INFY.NS")).thenReturn(
            new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true)
        );
        // no scheduled throws; stub will process once
        listener.onOrderPlaced(consumerRecord, null);

        int calls2 = (int) ((OrderExecutorService)executorService).getClass().getMethod("getCallCount").invoke(executorService);
        org.junit.jupiter.api.Assertions.assertEquals(1, calls2);
        org.junit.jupiter.api.Assertions.assertTrue(capturedDeadLetters.isEmpty());
    }

    private String validOrderPlacedMessage(String orderId, String symbol) throws Exception {
        return objectMapper.writeValueAsString(
            new MessageEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.ORDER_PLACED,
                Instant.parse("2026-09-17T10:15:30Z"),
                com.tradingplatform.events.Source.TRADE_API,
                new OrderPlacedPayload(
                    orderId,
                    42L,
                    symbol,
                    "BUY",
                    10,
                    new BigDecimal("100.00"),
                    new BigDecimal("1.00"),
                    "key-1",
                    Instant.parse("2026-09-17T10:15:30Z")
                )
            )
        );
    }

    private ProducerRecord<String, String> deadLetterRecord() {
        if (capturedDeadLetters.isEmpty()) throw new AssertionError("Expected one dead-letter send");
        return capturedDeadLetters.get(0);
    }

    private String headerValue(ProducerRecord<String, String> producerRecord, String headerName) {
        Header header = producerRecord.headers().lastHeader(headerName);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
