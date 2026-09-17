package com.tradingplatform.kafka;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

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

    @Mock
    private OrderExecutorService executorService;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private KafkaTemplate<String, String> deadLetterKafkaTemplate;

    private ObjectMapper objectMapper;
    private OrderPlacedListener listener;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        listener = new OrderPlacedListener(
            executorService,
            instrumentMapper,
            orderMapper,
            deadLetterKafkaTemplate,
            objectMapper
        );
    }

    @Test
    void malformedMessageIsDeadLetteredOnFirstAttempt() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 10L, "1", "{not-json");

        listener.onOrderPlaced(record);

        verifyNoInteractions(executorService, instrumentMapper, orderMapper);
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
                    "key-1",
                    Instant.parse("2026-09-17T10:15:30Z")
                )
            )
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 11L, "42", rawMessage);

        listener.onOrderPlaced(record);

        verifyNoInteractions(executorService, instrumentMapper, orderMapper);
        ProducerRecord<String, String> deadLetterRecord = deadLetterRecord();
        assertEquals("Missing required order field", headerValue(deadLetterRecord, "failure-reason"));
    }

    @Test
    void missingOrderInPostgresIsDeadLetteredImmediately() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String rawMessage = validOrderPlacedMessage(orderId, "INFY.NS");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 12L, "42", rawMessage);
        when(orderMapper.findOrderById(UUID.fromString(orderId))).thenReturn(null);

        listener.onOrderPlaced(record);

        verify(executorService, org.mockito.Mockito.never()).process(any(), any(Long.class), any());
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
                    "key-1",
                    Instant.parse("2026-09-17T10:15:30Z")
                )
            )
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 13L, "42", rawMessage);

        listener.onOrderPlaced(record);

        verifyNoInteractions(executorService, instrumentMapper, orderMapper);
        ProducerRecord<String, String> deadLetterRecord = deadLetterRecord();
        assertEquals("Unexpected event type: ORDER_FILLED", headerValue(deadLetterRecord, "failure-reason"));
    }

    @Test
    void transientFailureIsRetriedAndThenSucceeds() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String rawMessage = validOrderPlacedMessage(orderId, "INFY.NS");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 14L, "42", rawMessage);

        when(orderMapper.findOrderById(UUID.fromString(orderId))).thenReturn(
            new Order(42L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1")
        );
        when(instrumentMapper.findBySymbol("INFY.NS")).thenReturn(
            new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true)
        );
        doThrow(new PricingUnavailableException("broker unavailable"))
            .doNothing()
            .when(executorService)
            .process(eq(orderId), eq(42L), any());

        listener.onOrderPlaced(record);

        verify(executorService, org.mockito.Mockito.times(2)).process(eq(orderId), eq(42L), any());
        verifyNoInteractions(deadLetterKafkaTemplate);
    }

    @Test
    void poisonMessageDoesNotBlockPartition() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String rawMessage = validOrderPlacedMessage(orderId, "INFY.NS");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 15L, "42", rawMessage);
        when(orderMapper.findOrderById(UUID.fromString(orderId))).thenReturn(
            new Order(42L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1")
        );
        when(instrumentMapper.findBySymbol("INFY.NS")).thenReturn(
            new Instrument(100L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR", true)
        );
        doNothing().when(executorService).process(eq(orderId), eq(42L), any());

        listener.onOrderPlaced(record);

        verify(executorService).process(eq(orderId), eq(42L), any());
        verifyNoInteractions(deadLetterKafkaTemplate);
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
                    "key-1",
                    Instant.parse("2026-09-17T10:15:30Z")
                )
            )
        );
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> deadLetterRecord() {
        return org.mockito.Mockito.mockingDetails(deadLetterKafkaTemplate)
            .getInvocations()
            .stream()
            .filter(invocation -> invocation.getMethod().getName().equals("send"))
            .map(invocation -> (ProducerRecord<String, String>) invocation.getArguments()[0])
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected one dead-letter send"));
    }

    private String headerValue(ProducerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
