package com.tradingplatform.notifications.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.Source;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.notifications.channel.ChannelSender;
import com.tradingplatform.notifications.channel.NotificationMessage;
import com.tradingplatform.notifications.channel.NotificationDispatcher;
import com.tradingplatform.notifications.mapper.OrderNotificationMapper;
import com.tradingplatform.notifications.model.OrderDetails;
import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.PreferenceResolver;
import com.tradingplatform.preferences.ResolvedAlertChannels;

/**
 * Unit tests for TradeEventNotificationConsumer.
 *
 * Tests cover:
 * - FILLED / REJECTED events stored with the order's symbol, side, quantity and limit price
 * - Redelivered events, non-outcome events and malformed messages acknowledged without storing twice
 * - Database failures rethrown (offset not committed) so the message is retried
 */
@DisplayName("TradeEventNotificationConsumer Tests")
class TradeEventNotificationConsumerTest {

    private static final String ORDER_ID = "6f1c2c1e-6a49-4d3a-9b1e-2c7f0d9f1a10";
    private static final Instant EXECUTED_ON = Instant.parse("2026-10-06T09:05:00Z");

    private ObjectMapper objectMapper;
    private OrderNotificationMapper mapper;
    private Acknowledgment ack;
    private PreferenceResolver resolver;
    private ChannelSender email;
    private ChannelSender sms;
    private TradeEventNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper = mock(OrderNotificationMapper.class);
        ack = mock(Acknowledgment.class);
        resolver = mock(PreferenceResolver.class);
        when(resolver.resolveAlertChannels(7L)).thenReturn(ResolvedAlertChannels.platformDefault(7L));
        email = mock(ChannelSender.class);
        when(email.channel()).thenReturn(AlertChannel.EMAIL);
        sms = mock(ChannelSender.class);
        when(sms.channel()).thenReturn(AlertChannel.SMS);
        consumer = new TradeEventNotificationConsumer(objectMapper, mapper,
            new NotificationDispatcher(resolver, java.util.List.of(email, sms)));

        OrderDetails details = new OrderDetails();
        details.setSymbol("INFY.NS");
        details.setSide("BUY");
        details.setQuantity(40);
        details.setLimitPrice(new BigDecimal("1010.00"));
        when(mapper.findOrderDetails(ORDER_ID)).thenReturn(details);
        when(mapper.insertIgnoringDuplicate(any())).thenReturn(1);
    }

    @Test
    @DisplayName("Should store an ORDER_FILLED event enriched with the order's details")
    void testFilledEventStored() throws Exception {
        // Act
        consumer.handleTradeEvent(json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1006.35"), null), ack);

        // Assert
        OrderNotification n = captureStored();
        assertEquals("event-1", n.getEventId());
        assertEquals(7L, n.getAccountId());
        assertEquals(ORDER_ID, n.getOrderId());
        assertEquals("ORDER_FILLED", n.getEventType());
        assertEquals("FILLED", n.getStatus());
        assertEquals(0, new BigDecimal("1006.35").compareTo(n.getExecutedPrice()));
        assertEquals("INFY.NS", n.getSymbol());
        assertEquals("BUY", n.getSide());
        assertEquals(40, n.getQuantity());
        assertEquals(0, new BigDecimal("1010.00").compareTo(n.getLimitPrice()));
        assertEquals(EXECUTED_ON, n.getOccurredAt());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should email and text the customer when they chose those channels, and keep it out of the bell without IN_APP")
    void testSendsOnChosenChannels() throws Exception {
        // Arrange: customer chose EMAIL and SMS, not IN_APP
        when(resolver.resolveAlertChannels(7L)).thenReturn(new ResolvedAlertChannels(7L, java.util.List.of(
            new AlertTarget(AlertChannel.EMAIL, "rahul@example.com"),
            new AlertTarget(AlertChannel.SMS, "+447700900123")), ResolvedAlertChannels.Source.CUSTOMER, false));

        // Act
        consumer.handleTradeEvent(json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1006.35"), null), ack);

        // Assert
        OrderNotification n = captureStored();
        assertEquals("EMAIL,SMS", n.getChannels());
        assertFalse(n.isInApp());
        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(email).send(eq("rahul@example.com"), message.capture());
        verify(sms).send(eq("+447700900123"), any());
        assertEquals("Order completed", message.getValue().subject());
        assertEquals("Good news! You bought 40 INFY.NS at 1,006.35.", message.getValue().body());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should notify in-app only, and send no email or SMS, with the default preferences")
    void testDefaultIsInAppOnly() throws Exception {
        consumer.handleTradeEvent(json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1006.35"), null), ack);

        OrderNotification n = captureStored();
        assertEquals("IN_APP", n.getChannels());
        assertTrue(n.isInApp());
        verify(email, never()).send(anyString(), any());
        verify(sms, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("Should not email again when a redelivered event is already stored")
    void testNoResendOnRedelivery() throws Exception {
        when(resolver.resolveAlertChannels(7L)).thenReturn(new ResolvedAlertChannels(7L, java.util.List.of(
            new AlertTarget(AlertChannel.EMAIL, "rahul@example.com"), AlertTarget.inApp()),
            ResolvedAlertChannels.Source.CUSTOMER, false));
        when(mapper.insertIgnoringDuplicate(any())).thenReturn(0);

        consumer.handleTradeEvent(json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1006.35"), null), ack);

        verify(email, never()).send(anyString(), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should store an ORDER_REJECTED event with its reason")
    void testRejectedEventStored() throws Exception {
        consumer.handleTradeEvent(json(EventType.ORDER_REJECTED, "REJECTED", null, "Insufficient funds"), ack);

        OrderNotification n = captureStored();
        assertEquals("ORDER_REJECTED", n.getEventType());
        assertEquals("REJECTED", n.getStatus());
        assertEquals("Insufficient funds", n.getReason());
        assertNull(n.getExecutedPrice());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should acknowledge a redelivered event that is already stored")
    void testDuplicateAcknowledged() throws Exception {
        when(mapper.insertIgnoringDuplicate(any())).thenReturn(0);

        consumer.handleTradeEvent(json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1006.35"), null), ack);

        verify(mapper).insertIgnoringDuplicate(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should still store the notification when the order cannot be found")
    void testUnknownOrder() throws Exception {
        when(mapper.findOrderDetails(anyString())).thenReturn(null);

        consumer.handleTradeEvent(json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1"), null), ack);

        OrderNotification n = captureStored();
        assertNull(n.getSymbol());
        assertEquals("FILLED", n.getStatus());
    }

    @Test
    @DisplayName("Should skip events that are not order outcomes, and malformed messages")
    void testSkipsIrrelevantMessages() throws Exception {
        consumer.handleTradeEvent(json(EventType.QUOTE, "FILLED", null, null), ack);
        consumer.handleTradeEvent("{not json", ack);

        verify(mapper, never()).insertIgnoringDuplicate(any());
        verify(ack, times(2)).acknowledge();
    }

    @Test
    @DisplayName("Should skip an event without an account or order id")
    void testSkipsIncompleteEvent() throws Exception {
        TradeEventPayload payload = new TradeEventPayload(null, 7L, null, null, null, null, null, null, null,
            "FILLED", null, null, null, null, EXECUTED_ON);
        String message = objectMapper.writeValueAsString(
            new MessageEnvelope<>("event-2", EventType.ORDER_FILLED, EXECUTED_ON, Source.TRADE_EXECUTOR, payload));

        consumer.handleTradeEvent(message, ack);

        verify(mapper, never()).insertIgnoringDuplicate(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should rethrow a database failure without acknowledging, so the event is retried")
    void testDatabaseFailureRetried() throws Exception {
        when(mapper.insertIgnoringDuplicate(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        String message = json(EventType.ORDER_FILLED, "FILLED", new BigDecimal("1006.35"), null);

        assertThrows(DataAccessResourceFailureException.class, () -> consumer.handleTradeEvent(message, ack));
        verify(ack, never()).acknowledge();
    }

    private OrderNotification captureStored() {
        ArgumentCaptor<OrderNotification> captor = ArgumentCaptor.forClass(OrderNotification.class);
        verify(mapper).insertIgnoringDuplicate(captor.capture());
        return captor.getValue();
    }

    /** A trade event as the Trade Executor publishes it: no symbol, side or quantity */
    private String json(EventType type, String status, BigDecimal executedPrice, String reason) throws Exception {
        TradeEventPayload payload = new TradeEventPayload(ORDER_ID, 7L, null, null, null, null,
            new BigDecimal("1010.00"), executedPrice, new BigDecimal("2.50"), status, reason, null, null, null, EXECUTED_ON);
        return objectMapper.writeValueAsString(
            new MessageEnvelope<>("event-1", type, Instant.parse("2026-10-06T09:05:01Z"), Source.TRADE_EXECUTOR, 1, payload));
    }
}
