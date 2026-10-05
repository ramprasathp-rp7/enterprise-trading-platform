package com.tradingplatform.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.enums.OrderStatus;

class TradeEventPublisherTest {

    private static final Instant EXECUTED_ON = Instant.parse("2026-09-17T10:15:30Z");

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private TradeEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new TradeEventPublisher(kafkaTemplate, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sentRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void filledOrderIsPublishedAsOrderFilledKeyedByAccount() throws Exception {
        UUID orderId = UUID.randomUUID();

        publisher.publishTradeEvent(orderId, 42L, OrderStatus.FILLED, new BigDecimal("100.00"),
            new BigDecimal("99.50"), new BigDecimal("1.00"), null, EXECUTED_ON);

        ProducerRecord<String, String> record = sentRecord();
        assertEquals("trade-events", record.topic());
        assertEquals("42", record.key());

        JsonNode json = objectMapper.readTree(record.value());
        assertEquals("ORDER_FILLED", json.get("eventType").asText());
        assertEquals("trade-executor", json.get("source").asText());
        assertEquals(1, json.get("schemaVersion").asInt());
        JsonNode payload = json.get("payload");
        assertEquals(orderId.toString(), payload.get("orderId").asText());
        assertEquals("FILLED", payload.get("status").asText());
        // JsonNode drops trailing zeros, so compare numerically
        assertEquals(0, new BigDecimal("99.50").compareTo(payload.get("executedPrice").decimalValue()));
        assertEquals(0, BigDecimal.ONE.compareTo(payload.get("originalFee").decimalValue()));
    }

    @Test
    void rejectedOrderIsPublishedAsOrderRejectedWithReason() throws Exception {
        publisher.publishTradeEvent(UUID.randomUUID(), 7L, OrderStatus.REJECTED, new BigDecimal("100.00"),
            null, BigDecimal.ZERO, "Order rejected by fill rule", EXECUTED_ON);

        JsonNode json = objectMapper.readTree(sentRecord().value());
        assertEquals("ORDER_REJECTED", json.get("eventType").asText());
        assertEquals("Order rejected by fill rule", json.get("payload").get("reason").asText());
        assertNull(json.get("payload").get("executedPrice").textValue());
    }

    @Test
    void serialisationFailureIsWrappedAndNothingIsSent() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        TradeEventPublisher failingPublisher = new TradeEventPublisher(kafkaTemplate, failing);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> failingPublisher.publishTradeEvent(
            UUID.randomUUID(), 1L, OrderStatus.FILLED, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, EXECUTED_ON));
        assertEquals("Trade event serialization failed: boom", ex.getMessage());
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }
}
