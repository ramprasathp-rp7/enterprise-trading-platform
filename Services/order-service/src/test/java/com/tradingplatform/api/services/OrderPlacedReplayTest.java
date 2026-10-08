package com.tradingplatform.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;

/**
 * ORDER_PLACED replay when Kafka was down at placement.
 *
 * Tests cover:
 * - OrderPublisher.publishOrderPlaced: clears event_pending only when Kafka acknowledges it
 * - OrderReplayScheduler: republishes pending NEW orders and cancellations oldest first, stops at the first failure
 */
@DisplayName("ORDER_PLACED and ORDER_CANCELLED replay Tests")
class OrderPlacedReplayTest {

    private static final UUID FIRST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Order first;
    private Order second;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        first = new Order(FIRST_ID, 42L, 100L, OrderSide.BUY, 10, new BigDecimal("1500.00"), "key-1");
        second = new Order(SECOND_ID, 42L, 100L, OrderSide.BUY, 5, new BigDecimal("1510.00"), "key-2");
        instrument = Instrument.tradable(100L, "RELIANCE.NS", "Reliance Industries", AssetClass.EQUITY, "INR");
    }

    private static CompletableFuture<SendResult<String, MessageEnvelope<?>>> acknowledged() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("orders", 0), 7L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>("orders", "42", null), metadata));
    }

    @Test
    @DisplayName("Should mark the order published once Kafka acknowledges ORDER_PLACED")
    @SuppressWarnings("unchecked")
    void testAcknowledgedPublishMarksOrder() throws Exception {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(acknowledged());
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), orderMapper);

        Boolean published = publisher.publishOrderPlaced(first, instrument).get(5, TimeUnit.SECONDS);

        assertTrue(published);
        verify(orderMapper).markEventPublished(FIRST_ID, OrderStatus.NEW);
    }

    @Test
    @DisplayName("Should leave the order unpublished when Kafka rejects or cannot be reached")
    @SuppressWarnings("unchecked")
    void testFailedPublishDoesNotMarkOrder() throws Exception {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(kafka.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")))
            .thenThrow(new RuntimeException("metadata timeout"));
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), orderMapper);

        assertFalse(publisher.publishOrderPlaced(first, instrument).get(5, TimeUnit.SECONDS));
        assertFalse(publisher.publishOrderPlaced(first, instrument).get(5, TimeUnit.SECONDS));
        verify(orderMapper, never()).markEventPublished(any(), any());
    }

    @Test
    @DisplayName("Should still report success when the published mark cannot be written")
    @SuppressWarnings("unchecked")
    void testMarkFailureIsSwallowed() throws Exception {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(acknowledged());
        when(orderMapper.markEventPublished(FIRST_ID, OrderStatus.NEW)).thenThrow(new RuntimeException("db down"));
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper(), orderMapper);

        assertTrue(publisher.publishOrderPlaced(first, instrument).get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should republish unpublished NEW orders, oldest first, past the grace period")
    void testReplayPublishesPendingOrdersInOrder() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        when(orderMapper.findPendingNewOrders(any(Instant.class), anyInt())).thenReturn(List.of(first, second));
        when(instrumentMapper.findById(100L)).thenReturn(instrument);
        when(publisher.publishOrderPlaced(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        OrderReplayScheduler scheduler = new OrderReplayScheduler(orderMapper, instrumentMapper, publisher, 60_000, 100, 1_000);

        Instant before = Instant.now();
        scheduler.replayUnpublishedOrders();

        ArgumentCaptor<Instant> olderThan = ArgumentCaptor.forClass(Instant.class);
        verify(orderMapper).findPendingNewOrders(olderThan.capture(), eq(100));
        assertFalse(olderThan.getValue().isAfter(before.minusMillis(60_000)));
        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).publishOrderPlaced(first, instrument);
        inOrder.verify(publisher).publishOrderPlaced(second, instrument);
    }

    @Test
    @DisplayName("Should stop the run at the first failed publish while Kafka is still down")
    void testReplayStopsAtFirstFailure() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        when(orderMapper.findPendingNewOrders(any(Instant.class), anyInt())).thenReturn(List.of(first, second));
        when(instrumentMapper.findById(100L)).thenReturn(instrument);
        when(publisher.publishOrderPlaced(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
        OrderReplayScheduler scheduler = new OrderReplayScheduler(orderMapper, instrumentMapper, publisher, 60_000, 100, 1_000);

        scheduler.replayUnpublishedOrders();

        verify(publisher).publishOrderPlaced(first, instrument);
        verify(publisher, never()).publishOrderPlaced(eq(second), any());
    }

    @Test
    @DisplayName("Should not resend an order whose earlier replay is still pending in the producer")
    void testReplaySkipsInFlightOrder() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        when(orderMapper.findPendingNewOrders(any(Instant.class), anyInt())).thenReturn(List.of(first));
        when(instrumentMapper.findById(100L)).thenReturn(instrument);
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        when(publisher.publishOrderPlaced(any(), any())).thenReturn(pending);
        OrderReplayScheduler scheduler = new OrderReplayScheduler(orderMapper, instrumentMapper, publisher, 60_000, 100, 10);

        scheduler.replayUnpublishedOrders();   // times out, still buffered
        scheduler.replayUnpublishedOrders();   // skipped: in flight
        verify(publisher, times(1)).publishOrderPlaced(first, instrument);

        pending.complete(false);               // delivery finally failed
        scheduler.replayUnpublishedOrders();   // retried
        verify(publisher, times(2)).publishOrderPlaced(first, instrument);
    }

    @Test
    @DisplayName("Should do nothing when every NEW order has been published")
    void testReplayWithNothingPending() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        when(orderMapper.findPendingNewOrders(any(Instant.class), anyInt())).thenReturn(List.of());
        OrderReplayScheduler scheduler = new OrderReplayScheduler(orderMapper, mock(InstrumentMapper.class), publisher, 60_000, 100, 1_000);

        scheduler.replayUnpublishedOrders();

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Should republish pending cancellations oldest first, stamped with the stored cancel time")
    void testReplayPublishesPendingCancellations() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        Instant firstCancelled = Instant.parse("2026-10-08T09:00:00Z");
        Instant secondCancelled = Instant.parse("2026-10-08T09:05:00Z");
        first.setCancelledOn(firstCancelled);
        second.setCancelledOn(secondCancelled);
        when(orderMapper.findPendingCancellations(any(Instant.class), anyInt())).thenReturn(List.of(first, second));
        when(instrumentMapper.findById(100L)).thenReturn(instrument);
        when(publisher.publishOrderCancelled(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        OrderReplayScheduler scheduler = new OrderReplayScheduler(orderMapper, instrumentMapper, publisher, 60_000, 100, 1_000);

        scheduler.replayPendingCancellations();

        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).publishOrderCancelled(FIRST_ID, first, instrument, firstCancelled);
        inOrder.verify(publisher).publishOrderCancelled(SECOND_ID, second, instrument, secondCancelled);
        verify(publisher, never()).publishOrderPlaced(any(), any());
    }

    @Test
    @DisplayName("Should stop replaying cancellations at the first failed publish")
    void testCancellationReplayStopsAtFirstFailure() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        OrderPublisher publisher = mock(OrderPublisher.class);
        when(orderMapper.findPendingCancellations(any(Instant.class), anyInt())).thenReturn(List.of(first, second));
        when(instrumentMapper.findById(100L)).thenReturn(instrument);
        when(publisher.publishOrderCancelled(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(false));
        OrderReplayScheduler scheduler = new OrderReplayScheduler(orderMapper, instrumentMapper, publisher, 60_000, 100, 1_000);

        scheduler.replayPendingCancellations();

        verify(publisher).publishOrderCancelled(eq(FIRST_ID), any(), any(), any());
        verify(publisher, never()).publishOrderCancelled(eq(SECOND_ID), any(), any(), any());
    }
}
