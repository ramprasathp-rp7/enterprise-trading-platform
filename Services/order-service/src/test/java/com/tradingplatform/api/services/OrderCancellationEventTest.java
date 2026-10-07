package com.tradingplatform.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

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
import com.tradingplatform.domain.exception.OrderNotCancellableException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;

/**
 * ORDER_CANCELLED on the trade-events topic when a customer cancels an order.
 *
 * Tests cover:
 * - OrderPublisher.publishOrderCancelled: topic, key, envelope and payload
 * - OrderService.cancelOrderById: publishes only when the order was actually cancelled
 */
@DisplayName("Order cancellation event Tests")
class OrderCancellationEventTest {

    private static final UUID ORDER_ID = UUID.fromString("6f1c2c1e-6a49-4d3a-9b1e-2c7f0d9f1a10");

    private Order order;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        order = new Order(ORDER_ID, 42L, 100L, OrderSide.SELL, 30, new BigDecimal("1215.70"), "key-1");
        instrument = Instrument.tradable(100L, "RELIANCE.NS", "Reliance Industries", AssetClass.EQUITY, "INR");
    }

    @Test
    @DisplayName("Should publish ORDER_CANCELLED to trade-events, keyed by account, with the order's details")
    @SuppressWarnings("unchecked")
    void testPublishOrderCancelled() {
        // Arrange
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper());

        // Act
        publisher.publishOrderCancelled(ORDER_ID, order, instrument);

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
        assertNotNull(payload.executedOn());
    }

    @Test
    @DisplayName("Should not throw when Kafka is unavailable; the cancellation is already saved")
    @SuppressWarnings("unchecked")
    void testPublishFailureIsSwallowed() {
        KafkaTemplate<String, MessageEnvelope<?>> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("broker down"));
        OrderPublisher publisher = new OrderPublisher(kafka, new ObjectMapper());

        assertDoesNotThrow(() -> publisher.publishOrderCancelled(ORDER_ID, order, instrument));
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
        when(orderMapper.findOrderById(ORDER_ID)).thenReturn(order);
        when(instrumentMapper.findById(100L)).thenReturn(instrument);

        // Act: cancellable
        when(orderMapper.cancelOrderIfNew(ORDER_ID)).thenReturn(1);
        service.cancelOrderById(ORDER_ID.toString());

        // Assert
        verify(publisher).publishOrderCancelled(ORDER_ID, order, instrument);

        // Act: already filled/rejected/cancelled
        clearInvocations(publisher);
        when(orderMapper.cancelOrderIfNew(ORDER_ID)).thenReturn(0);
        assertThrows(OrderNotCancellableException.class, () -> service.cancelOrderById(ORDER_ID.toString()));
        verifyNoInteractions(publisher);
    }
}
