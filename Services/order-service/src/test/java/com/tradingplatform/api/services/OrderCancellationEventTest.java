package com.tradingplatform.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.Source;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.OrderNotCancellableException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;

/**
 * ORDER_CANCELLED on the trade-events topic when a customer cancels an order.
 *
 * Tests cover:
 * - OrderPublisher.publishOrderCancelled: topic, key, envelope and payload, the stored cancel time,
 *   a stable eventId, and event_pending cleared only on acknowledgement
 * - OrderService.cancelOrderById: publishes only when the order was actually cancelled
 */
@DisplayName("Order cancellation event Tests")
class OrderCancellationEventTest {

    private static final UUID ORDER_ID = UUID.fromString("6f1c2c1e-6a49-4d3a-9b1e-2c7f0d9f1a10");
    private static final Instant CANCELLED_ON = Instant.parse("2026-10-08T09:15:00Z");

    private Order order;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        order = new Order(ORDER_ID, 42L, 100L, OrderSide.SELL, 30, new BigDecimal("1215.70"), "key-1");
        instrument = Instrument.tradable(100L, "RELIANCE.NS", "Reliance Industries", AssetClass.EQUITY, "INR");
    }

    private static CompletableFuture<SendResult<String, MessageEnvelope<?>>> acknowledged() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("trade-events", 0), 3L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>("trade-events", "42", null), metadata));
    }

    @Test
    @DisplayName("Should publish ORDER_CANCELLED to trade-events, keyed by account, with the order's details")
    @SuppressWarnings("unchecked")
    void testPublishOrderCancelled() {
        // Arrange
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), mock(OrderMapper.class));

        // Act
        publisher.publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON);

        // Assert
        ArgumentCaptor<MessageEnvelope<?>> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(kafka).send(eq("trade-events"), eq("42"), captor.capture());
        MessageEnvelope<?> envelope = captor.getValue();
        assertEquals(EventType.ORDER_CANCELLED, envelope.eventType());
        assertEquals(Source.TRADE_API, envelope.source());
        assertNotNull(envelope.eventId());

        TradeEventPayload payload = (TradeEventPayload) envelope.payload();
        assertEquals(ORDER_ID.toString(), payload.orderId());
        assertEquals(42L, payload.accountId());
        assertEquals("RELIANCE.NS", payload.symbol());
        assertEquals("SELL", payload.side());
        assertEquals(30, payload.quantity());
        assertEquals(new BigDecimal("1215.70"), payload.price());
        assertEquals("CANCELLED", payload.status());
        assertEquals("CANCELLED_BY_CUSTOMER", payload.reason());
        assertNull(payload.executedPrice());
        // The stored cancel time, not the publish time, so live and replayed events agree
        assertEquals(CANCELLED_ON, payload.executedOn());
        assertEquals(CANCELLED_ON, envelope.eventTime());
    }

    @Test
    @DisplayName("Should use the same eventId every time an order's ORDER_CANCELLED is published")
    @SuppressWarnings("unchecked")
    void testEventIdIsStableAcrossReplays() {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), mock(OrderMapper.class));

        publisher.publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON);
        publisher.publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON);

        ArgumentCaptor<MessageEnvelope<?>> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(kafka, times(2)).send(eq("trade-events"), eq("42"), captor.capture());
        assertEquals(captor.getAllValues().get(0).eventId(), captor.getAllValues().get(1).eventId());
        assertEquals(OrderPublisher.outcomeEventId(EventType.ORDER_CANCELLED, ORDER_ID), captor.getValue().eventId());
    }

    @Test
    @DisplayName("Should clear event_pending for the CANCELLED status only once Kafka acknowledges the event")
    @SuppressWarnings("unchecked")
    void testAcknowledgementClearsPendingEvent() throws Exception {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(kafka.send(anyString(), anyString(), any()))
            .thenReturn(acknowledged())
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), orderMapper);

        assertTrue(publisher.publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON).get(5, TimeUnit.SECONDS));
        verify(orderMapper).markEventPublished(ORDER_ID, OrderStatus.CANCELLED);

        clearInvocations(orderMapper);
        assertFalse(publisher.publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON).get(5, TimeUnit.SECONDS));
        verify(orderMapper, never()).markEventPublished(any(), any());
    }

    @Test
    @DisplayName("Should not throw when Kafka is unavailable; the cancellation is already saved")
    @SuppressWarnings("unchecked")
    void testPublishFailureIsSwallowed() {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("broker down"));
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), mock(OrderMapper.class));

        assertDoesNotThrow(() -> assertFalse(
            publisher.publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON).get(5, TimeUnit.SECONDS)));
    }

    @Test
    @DisplayName("Should publish ORDER_CANCELLED when a NEW order is cancelled, and not when it cannot be")
    void testCancelOrderPublishesOnlyOnSuccess() {
        // Arrange
        OrderMapper orderMapper = mock(OrderMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        OrderService service = new OrderService(mock(OrderValidationService.class), mock(OrderSettlementService.class),
            orderMapper, mock(AccountMapper.class), instrumentMapper, mock(AssetFeeMapper.class),
            mock(AccountAccessAuthorizer.class), publisher);
        order.setCancelledOn(CANCELLED_ON);
        when(orderMapper.findOrderById(ORDER_ID)).thenReturn(order);
        when(instrumentMapper.findById(100L)).thenReturn(instrument);

        // Act: cancellable
        when(orderMapper.cancelOrderIfNew(ORDER_ID)).thenReturn(1);
        service.cancelOrderById(ORDER_ID.toString());

        // Assert: published with the cancel time the database recorded
        verify(publisher).publishOrderCancelled(ORDER_ID, order, instrument, CANCELLED_ON);

        // Act: already filled/rejected/cancelled
        clearInvocations(publisher);
        when(orderMapper.cancelOrderIfNew(ORDER_ID)).thenReturn(0);
        assertThrows(OrderNotCancellableException.class, () -> service.cancelOrderById(ORDER_ID.toString()));
        verifyNoInteractions(publisher);
    }
}
