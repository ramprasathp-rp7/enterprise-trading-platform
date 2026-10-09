package com.tradingplatform.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.AssetClass;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.OrderPlacedPayload;
import com.tradingplatform.events.Source;
import com.tradingplatform.executor.OrderExecutorService;
import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.mappers.OrderMapper;
import com.tradingplatform.quote.PricingUnavailableException;

/**
 * Covers the validation, retry and dead-letter branches of {@link OrderPlacedListener}
 * using a mocked executor so each failure mode can be injected directly.
 */
class OrderPlacedListenerBranchTest {

    private OrderExecutorService executorService;
    private InstrumentMapper instrumentMapper;
    private OrderMapper orderMapper;
    private Acknowledgment ack;
    private ObjectMapper objectMapper;
    private final List<ProducerRecord<String, String>> deadLetters = new ArrayList<>();
    private OrderPlacedListener listener;

    private final String orderId = UUID.randomUUID().toString();
    private final UUID orderUuid = UUID.fromString(orderId);

    @BeforeEach
    void setUp() {
        executorService = mock(OrderExecutorService.class);
        instrumentMapper = mock(InstrumentMapper.class);
        orderMapper = mock(OrderMapper.class);
        ack = mock(Acknowledgment.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        listener = new OrderPlacedListener(executorService, instrumentMapper, orderMapper, producerRecord -> {
            deadLetters.add(producerRecord);
            return CompletableFuture.completedFuture(null);
        }, objectMapper);

        when(orderMapper.findOrderById(orderUuid)).thenReturn(
            new Order(orderUuid, 42L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1"));
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(
            Instrument.tradable(100L, "AAPL", "Apple", AssetClass.EQUITY, "USD"));
    }

    private OrderPlacedPayload payload(String id, String symbol, String side, String idempotencyKey) {
        return new OrderPlacedPayload(id, 42L, symbol, side, 10, new BigDecimal("100.00"),
            new BigDecimal("1.25"), idempotencyKey, Instant.parse("2026-09-17T10:15:30Z"));
    }

    private ConsumerRecord<String, String> consumerRecord(OrderPlacedPayload payload) throws Exception {
        String json = objectMapper.writeValueAsString(new MessageEnvelope<>(UUID.randomUUID().toString(),
            EventType.ORDER_PLACED, Instant.parse("2026-09-17T10:15:30Z"), Source.TRADE_API, payload));
        return new ConsumerRecord<>("orders", 2, 99L, "42", json);
    }

    private String failureReason(ProducerRecord<String, String> producerRecord) {
        return new String(producerRecord.headers().lastHeader("failure-reason").value(), StandardCharsets.UTF_8);
    }

    @Test
    void validOrderIsExecutedAndAcknowledged() throws Exception {
        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "buy", "key-1")), ack);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(executorService).process(captor.capture(), eq(new BigDecimal("100.00")), eq(new BigDecimal("1.25")));
        Order built = captor.getValue();
        assertEquals(orderUuid, built.getId());
        assertEquals(OrderSide.BUY, built.getSide());
        assertEquals(100L, built.getInstrumentId());
        verify(ack).acknowledge();
        assertTrue(deadLetters.isEmpty());
    }

    @Test
    void cancelledOrderNeverEntersTheExecutor() throws Exception {
        when(orderMapper.findOrderStatus(orderUuid)).thenReturn(OrderStatus.CANCELLED);

        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "BUY", "key-1")), ack);

        verifyNoInteractions(executorService);
        verify(orderMapper, never()).updateOrderStatus(any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
        assertTrue(deadLetters.isEmpty());
    }

    @Test
    void unknownSymbolPassesNullOrderToExecutor() throws Exception {
        when(instrumentMapper.findBySymbol("ZZZ")).thenReturn(null);

        listener.onOrderPlaced(consumerRecord(payload(orderId, "ZZZ", "SELL", "key-1")), ack);

        verify(executorService).process(isNull(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void malformedJsonIsDeadLetteredAndAcknowledged() {
        listener.onOrderPlaced(new ConsumerRecord<>("orders", 0, 1L, "k", "{oops"), ack);

        verify(ack).acknowledge();
        assertEquals(1, deadLetters.size());
        ProducerRecord<String, String> dlt = deadLetters.get(0);
        assertEquals("orders.DLT", dlt.topic());
        assertEquals("k", dlt.key());
        assertEquals("0", new String(dlt.headers().lastHeader("original-partition").value(), StandardCharsets.UTF_8));
        assertEquals("1", new String(dlt.headers().lastHeader("original-offset").value(), StandardCharsets.UTF_8));
        assertEquals("orders", new String(dlt.headers().lastHeader("original-topic").value(), StandardCharsets.UTF_8));
    }

    @Test
    void unexpectedEventTypeIsAcknowledged() throws Exception {
        String json = objectMapper.writeValueAsString(new MessageEnvelope<>("e", EventType.QUOTE,
            Instant.now(), Source.MARKET_POLLER, payload(orderId, "AAPL", "BUY", "k")));

        listener.onOrderPlaced(new ConsumerRecord<>("orders", 0, 1L, "k", json), ack);

        verify(ack).acknowledge();
        assertEquals("Unexpected event type: QUOTE", failureReason(deadLetters.get(0)));
    }

    @Test
    void nullPayloadIsDeadLettered() throws Exception {
        listener.onOrderPlaced(consumerRecord(null), ack);

        verify(ack).acknowledge();
        assertEquals("Missing required order field", failureReason(deadLetters.get(0)));
    }

    @Test
    void blankRequiredFieldsAreDeadLettered() throws Exception {
        OrderPlacedPayload[] invalid = {
            payload(" ", "AAPL", "BUY", "k"),
            payload(orderId, " ", "BUY", "k"),
            payload(orderId, null, "BUY", "k"),
            payload(orderId, "AAPL", "", "k"),
            payload(orderId, "AAPL", null, "k"),
            payload(orderId, "AAPL", "BUY", " "),
            payload(orderId, "AAPL", "BUY", null),
            new OrderPlacedPayload(orderId, null, "AAPL", "BUY", 1, BigDecimal.ONE, null, "k", null),
            new OrderPlacedPayload(orderId, 1L, "AAPL", "BUY", null, BigDecimal.ONE, null, "k", null),
            new OrderPlacedPayload(orderId, 1L, "AAPL", "BUY", 1, null, null, "k", null),
        };
        for (OrderPlacedPayload p : invalid) {
            listener.onOrderPlaced(consumerRecord(p), ack);
        }

        assertEquals(invalid.length, deadLetters.size());
        deadLetters.forEach(d -> assertEquals("Missing required order field", failureReason(d)));
        verify(ack, times(invalid.length)).acknowledge();
        verifyNoInteractions(executorService);
    }

    @Test
    void invalidOrderIdIsDeadLettered() throws Exception {
        listener.onOrderPlaced(consumerRecord(payload("not-a-uuid", "AAPL", "BUY", "k")), ack);

        verify(ack).acknowledge();
        assertEquals("Invalid order identifier: not-a-uuid", failureReason(deadLetters.get(0)));
        verifyNoInteractions(executorService, orderMapper);
    }

    @Test
    void unknownSideIsDeadLetteredWithoutRetry() throws Exception {
        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "HOLD", "k")), ack);

        verify(ack).acknowledge();
        assertEquals(1, deadLetters.size());
        assertTrue(failureReason(deadLetters.get(0)).contains("HOLD"));
        verifyNoInteractions(executorService);
    }

    @Test
    void orderMissingInExecutorIsDeadLettered() throws Exception {
        doThrow(new java.util.NoSuchElementException("Order not found: x"))
            .when(executorService).process(any(), any(), any());

        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "BUY", "k")), ack);

        verify(executorService, times(1)).process(any(), any(), any());
        assertEquals("Order not found: x", failureReason(deadLetters.get(0)));
        verify(ack).acknowledge();
    }

    @Test
    void persistentPricingOutageRejectsOrderInsteadOfDeadLettering() throws Exception {
        doThrow(new PricingUnavailableException("quotes down"))
            .when(executorService).process(any(), any(), any());

        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "BUY", "k")), ack);

        verify(executorService, times(3)).process(any(), any(), any());
        verify(executorService).rejectNew(eq(orderUuid), anyLong(),
            eq("Pricing service unavailable: quotes down"), any(), any());
        assertTrue(deadLetters.isEmpty());
        verify(ack).acknowledge();
    }

    @Test
    void persistentDatabaseFailureIsDeadLetteredAfterRetries() throws Exception {
        doThrow(new TransientDataAccessResourceException("db down"))
            .when(executorService).process(any(), any(), any());

        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "BUY", "k")), ack);

        verify(executorService, times(3)).process(any(), any(), any());
        assertEquals("db down", failureReason(deadLetters.get(0)));
        verify(orderMapper, never()).updateOrderStatus(any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void transientDatabaseFailureRecoversOnRetry() throws Exception {
        doThrow(new TransientDataAccessResourceException("blip"))
            .doNothing()
            .when(executorService).process(any(), any(), any());

        listener.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "BUY", "k")), null);

        verify(executorService, times(2)).process(any(), any(), any());
        assertTrue(deadLetters.isEmpty());
    }

    @Test
    void springConstructorSendsDeadLettersThroughKafkaTemplate() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        OrderPlacedListener springListener = new OrderPlacedListener(
            executorService, instrumentMapper, orderMapper, template, objectMapper, 1000L, 1000L);

        springListener.onOrderPlaced(new ConsumerRecord<>("orders", 0, 5L, null, "not json"), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        assertEquals("orders.DLT", captor.getValue().topic());
        assertNull(captor.getValue().key());
        assertNotNull(captor.getValue().headers().lastHeader("failure-reason"));
    }

    @Test
    void deadLetterIsAcknowledgedOnlyOnceKafkaHasIt() {
        listener.onOrderPlaced(new ConsumerRecord<>("orders", 0, 1L, "k", "{oops"), ack);

        verify(ack).acknowledge();
        verify(ack, never()).nack(any(Duration.class));
    }

    @Test
    void failedDeadLetterSendIsNackedForRedeliveryNotAcknowledged() {
        OrderPlacedListener failing = new OrderPlacedListener(executorService, instrumentMapper, orderMapper,
            producerRecord -> CompletableFuture.failedFuture(new RuntimeException("broker down")), objectMapper, 1000L, 2500L);

        failing.onOrderPlaced(new ConsumerRecord<>("orders", 0, 1L, "k", "{oops"), ack);

        verify(ack, never()).acknowledge();
        verify(ack).nack(Duration.ofMillis(2500L));
    }

    @Test
    void deadLetterSendThatTimesOutIsNacked() throws Exception {
        OrderPlacedListener slow = new OrderPlacedListener(executorService, instrumentMapper, orderMapper,
            producerRecord -> new CompletableFuture<>(), objectMapper, 10L, 2500L);

        slow.onOrderPlaced(consumerRecord(payload(orderId, "AAPL", "BUY", null)), ack);

        verify(ack, never()).acknowledge();
        verify(ack).nack(Duration.ofMillis(2500L));
    }
}
