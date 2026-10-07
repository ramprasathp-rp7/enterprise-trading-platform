package com.tradingplatform.notifications.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.domain.exception.AccountUnauthorizedException;
import com.tradingplatform.notifications.dto.OrderNotificationResponse;
import com.tradingplatform.notifications.mapper.OrderNotificationMapper;
import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.notifications.service.OrderNotificationService;

/**
 * Unit tests for OrderNotificationController and OrderNotificationService.
 *
 * Tests cover:
 * - Cursor reads (after=) oldest first, latest reads reversed to oldest first
 * - Limit clamping
 * - Account access check before any read
 */
@DisplayName("OrderNotificationController Tests")
class OrderNotificationControllerTest {

    private OrderNotificationMapper mapper;
    private AccountAccessAuthorizer authorizer;
    private OrderNotificationController controller;

    @BeforeEach
    void setUp() {
        mapper = mock(OrderNotificationMapper.class);
        authorizer = mock(AccountAccessAuthorizer.class);
        controller = new OrderNotificationController(new OrderNotificationService(mapper), authorizer);
    }

    @Test
    @DisplayName("Should return notifications after the cursor, oldest first")
    void testAfterCursor() {
        when(mapper.findAfter(7L, 10L, 50)).thenReturn(List.of(notification(11, "FILLED"), notification(12, "REJECTED")));

        ResponseEntity<List<OrderNotificationResponse>> response = controller.getOrderNotifications(7L, 10L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<OrderNotificationResponse> body = response.getBody();
        assertEquals(List.of(11L, 12L), body.stream().map(OrderNotificationResponse::id).toList());
        OrderNotificationResponse first = body.get(0);
        assertEquals("ORDER_FILLED", first.eventType());
        assertEquals("FILLED", first.status());
        assertEquals("INFY.NS", first.symbol());
        assertEquals("BUY", first.side());
        assertEquals(40, first.quantity());
        assertEquals(0, new BigDecimal("1010.00").compareTo(first.price()));
        assertEquals(0, new BigDecimal("1006.35").compareTo(first.executedPrice()));
        verify(authorizer).assertTokenCanAccess(7L);
    }

    @Test
    @DisplayName("Should return the latest notifications oldest first when there is no cursor")
    void testLatest() {
        when(mapper.findLatest(7L, 50)).thenReturn(List.of(notification(12, "FILLED"), notification(11, "FILLED")));

        List<OrderNotificationResponse> body = controller.getOrderNotifications(7L, null, null).getBody();

        assertEquals(List.of(11L, 12L), body.stream().map(OrderNotificationResponse::id).toList());
    }

    @Test
    @DisplayName("Should clamp the limit to 1..200")
    void testLimitClamped() {
        controller.getOrderNotifications(7L, 0L, 5000);
        controller.getOrderNotifications(7L, 0L, 0);

        verify(mapper).findAfter(7L, 0L, 200);
        verify(mapper).findAfter(7L, 0L, 1);
    }

    @Test
    @DisplayName("Should refuse another account's notifications before reading them")
    void testAccountAccessChecked() {
        doThrow(new AccountUnauthorizedException(8L, 7L)).when(authorizer).assertTokenCanAccess(8L);

        assertThrows(AccountUnauthorizedException.class, () -> controller.getOrderNotifications(8L, null, null));
        verify(mapper, never()).findLatest(anyLong(), anyInt());
    }

    private static OrderNotification notification(long id, String status) {
        OrderNotification n = new OrderNotification();
        n.setNotificationId(id);
        n.setEventId("event-" + id);
        n.setAccountId(7L);
        n.setOrderId("6f1c2c1e-6a49-4d3a-9b1e-2c7f0d9f1a10");
        n.setEventType("ORDER_" + status);
        n.setStatus(status);
        n.setSymbol("INFY.NS");
        n.setSide("BUY");
        n.setQuantity(40);
        n.setLimitPrice(new BigDecimal("1010.00"));
        n.setExecutedPrice("FILLED".equals(status) ? new BigDecimal("1006.35") : null);
        n.setReason("REJECTED".equals(status) ? "Insufficient funds" : null);
        n.setOccurredAt(Instant.parse("2026-10-06T09:05:00Z"));
        return n;
    }
}
