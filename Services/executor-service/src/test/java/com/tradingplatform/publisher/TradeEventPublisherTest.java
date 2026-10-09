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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.tradingplatform.domain.PendingOutcome;
import com.tradingplatform.events.EventType;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;

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
import com.tradingplatform.mappers.OrderMapper;

class TradeEventPublisherTest {

    private static final Instant EXECUTED_ON = Instant.parse("2026-09-17T10:15:30Z");

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private OrderMapper orderMapper;
    private TradeEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        orderMapper = mock(OrderMapper.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        publisher = new TradeEventPublisher(kafkaTemplate, objectMapper, orderMapper);
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

        publisher.publishOutcome(new PendingOutcome(orderId, 42L, OrderStatus.FILLED, new BigDecimal("100.00"),
            new BigDecimal("99.50"), new BigDecimal("1.00"), null, EXECUTED_ON, null));

        ProducerRecord<String, String> producerRecord = sentRecord();
        assertEquals("trade-events", producerRecord.topic());
        assertEquals("42", producerRecord.key());

        JsonNode json = objectMapper.readTree(producerRecord.value());
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
        publisher.publishOutcome(new PendingOutcome(UUID.randomUUID(), 7L, OrderStatus.REJECTED, new BigDecimal("100.00"),
            null, BigDecimal.ZERO, "Order rejected by fill rule", EXECUTED_ON, null));

        JsonNode json = objectMapper.readTree(sentRecord().value());
        assertEquals("ORDER_REJECTED", json.get("eventType").asText());
        assertEquals("Order rejected by fill rule", json.get("payload").get("reason").asText());
        assertNull(json.get("payload").get("executedPrice").textValue());
    }

    @Test
    void serialisationFailureIsWrappedAndNothingIsSent() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        TradeEventPublisher failingPublisher = new TradeEventPublisher(kafkaTemplate, failing, orderMapper);

        PendingOutcome outcome = new PendingOutcome(UUID.randomUUID(), 1L, OrderStatus.FILLED, BigDecimal.ONE,
            BigDecimal.ONE, BigDecimal.ZERO, null, EXECUTED_ON, null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> failingPublisher.publishOutcome(outcome));
        assertEquals("Trade event serialization failed: boom", ex.getMessage());
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("trade-events", 0), 5L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>("trade-events", "42", "{}"), metadata));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgedEventClearsPendingForItsStatus() throws Exception {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(acknowledged());
        UUID orderId = UUID.randomUUID();

        Boolean published = publisher.publishOutcome(new PendingOutcome(orderId, 42L, OrderStatus.FILLED, BigDecimal.TEN,
            BigDecimal.TEN, BigDecimal.ONE, null, EXECUTED_ON, null)).get(5, TimeUnit.SECONDS);

        assertTrue(published);
        verify(orderMapper).markEventPublished(orderId, OrderStatus.FILLED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedSendLeavesTheEventPending() throws Exception {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")))
            .thenThrow(new RuntimeException("metadata timeout"));

        assertFalse(publisher.publishOutcome(new PendingOutcome(UUID.randomUUID(), 42L, OrderStatus.REJECTED, BigDecimal.TEN,
            null, BigDecimal.ONE, "No price available", EXECUTED_ON, null)).get(5, TimeUnit.SECONDS));
        assertFalse(publisher.publishOutcome(new PendingOutcome(UUID.randomUUID(), 42L, OrderStatus.REJECTED, BigDecimal.TEN,
            null, BigDecimal.ONE, "No price available", EXECUTED_ON, null)).get(5, TimeUnit.SECONDS));
        verify(orderMapper, never()).markEventPublished(any(), any());
    }

    @Test
    void insideATransactionTheEventIsSentOnlyAfterCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishOutcome(new PendingOutcome(UUID.randomUUID(), 42L, OrderStatus.FILLED, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.ONE, null, EXECUTED_ON, null));
            verify(kafkaTemplate, never()).send(any(ProducerRecord.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(kafkaTemplate).send(any(ProducerRecord.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyPublishOfAnOrdersEventCarriesTheSameEventId() throws Exception {
        UUID orderId = UUID.randomUUID();
        publisher.publishOutcome(new PendingOutcome(orderId, 42L, OrderStatus.FILLED, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, null, EXECUTED_ON, null));
        publisher.publishOutcome(new PendingOutcome(orderId, 42L, OrderStatus.FILLED, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, null, EXECUTED_ON, null));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());
        String first = objectMapper.readTree(captor.getAllValues().get(0).value()).get("eventId").asText();
        String second = objectMapper.readTree(captor.getAllValues().get(1).value()).get("eventId").asText();
        assertEquals(first, second);
        assertEquals(TradeEventPublisher.outcomeEventId(EventType.ORDER_FILLED, orderId), first);
    }

    @Test
    void replayedOutcomeIsRebuiltFromTheOrderRow() throws Exception {
        UUID orderId = UUID.randomUUID();
        PendingOutcome outcome = new PendingOutcome(orderId, 42L, OrderStatus.FILLED, new BigDecimal("100.00"),
            new BigDecimal("99.50"), new BigDecimal("1.25"), null, EXECUTED_ON, new BigDecimal("83.10"));

        publisher.publishOutcome(outcome);

        JsonNode json = objectMapper.readTree(sentRecord().value());
        assertEquals("ORDER_FILLED", json.get("eventType").asText());
        JsonNode payload = json.get("payload");
        assertEquals(orderId.toString(), payload.get("orderId").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(payload.get("quotedPrice").decimalValue()));
        assertEquals(0, new BigDecimal("99.50").compareTo(payload.get("executedPrice").decimalValue()));
        assertEquals(0, new BigDecimal("1.25").compareTo(payload.get("originalFee").decimalValue()));
        assertEquals(0, new BigDecimal("83.10").compareTo(payload.get("fillFxRate").decimalValue()));
        // The test mapper writes timestamps as epoch seconds
        assertEquals(EXECUTED_ON.getEpochSecond(), payload.get("executedOn").asLong());
    }
}
