package com.tradingplatform.notifications.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.notifications.dto.NotificationHistoryItem;
import com.tradingplatform.notifications.mapper.OrderNotificationMapper;
import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;

class NotificationHistoryServiceTest {

    private OrderNotificationMapper orders;
    private PriceAlertTriggerMapper triggers;
    private NotificationHistoryService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderNotificationMapper.class);
        triggers = mock(PriceAlertTriggerMapper.class);
        service = new NotificationHistoryService(orders, triggers);
    }

    @Test
    void mergesOrdersAndAlertsNewestFirst_withTheirChannelsAndDetails() {
        when(orders.findHistory(eq(2L), anyInt())).thenReturn(List.of(
            order(11L, "FILLED", "2026-10-09T10:00:00Z", "EMAIL,SMS,IN_APP", true),
            order(10L, "REJECTED", "2026-10-09T08:00:00Z", "EMAIL", false)));
        when(triggers.findHistoryByAccountId(eq(2L), anyInt())).thenReturn(List.of(
            trigger(7L, "2026-10-09T09:00:00Z", null)));

        List<NotificationHistoryItem> items = service.history(2L, null);

        assertEquals(List.of("order-11", "alert-7", "order-10"), items.stream().map(NotificationHistoryItem::id).toList());

        NotificationHistoryItem filled = items.get(0);
        assertEquals(NotificationHistoryItem.Kind.ORDER, filled.kind());
        assertEquals("FILLED", filled.status());
        assertEquals(List.of("EMAIL", "SMS", "IN_APP"), filled.channels());
        assertEquals("o-11", filled.order().orderId());
        assertEquals(new BigDecimal("189.50"), filled.order().executedPrice());
        assertNull(filled.priceAlert());

        NotificationHistoryItem alert = items.get(1);
        assertEquals(NotificationHistoryItem.Kind.PRICE_ALERT, alert.kind());
        assertEquals("DELIVERED", alert.status());
        assertEquals(List.of("IN_APP"), alert.channels(), "no channels recorded means the in-app default");
        assertEquals("ABOVE", alert.priceAlert().direction());
        assertEquals("AAPL 191 >= 190 (ABOVE, ONCE)", alert.priceAlert().reason());

        NotificationHistoryItem rejected = items.get(2);
        assertFalse(rejected.inApp(), "history includes notifications that never reached the bell");
        assertEquals(List.of("EMAIL"), rejected.channels());
    }

    @Test
    void limitIsClampedAndApplied() {
        when(orders.findHistory(eq(2L), anyInt())).thenReturn(List.of(
            order(3L, "FILLED", "2026-10-09T10:00:00Z", "IN_APP", true),
            order(2L, "FILLED", "2026-10-09T09:00:00Z", "IN_APP", true),
            order(1L, "FILLED", "2026-10-09T08:00:00Z", "IN_APP", true)));
        when(triggers.findHistoryByAccountId(eq(2L), anyInt())).thenReturn(List.of());

        assertEquals(2, service.history(2L, 2).size());
        service.history(2L, 5000);
        verify(orders).findHistory(2L, NotificationHistoryService.MAX_LIMIT);
        service.history(2L, -1);
        verify(orders).findHistory(2L, 1);
    }

    private static OrderNotification order(long id, String status, String at, String channels, boolean inApp) {
        OrderNotification n = new OrderNotification();
        n.setNotificationId(id);
        n.setOrderId("o-" + id);
        n.setEventType("ORDER_" + status);
        n.setStatus(status);
        n.setSymbol("AAPL");
        n.setSide("BUY");
        n.setQuantity(10);
        n.setLimitPrice(new BigDecimal("190.00"));
        n.setExecutedPrice(new BigDecimal("189.50"));
        n.setOccurredAt(Instant.parse(at));
        n.setChannels(channels);
        n.setInApp(inApp);
        return n;
    }

    private static PriceAlertTrigger trigger(long id, String at, String channels) {
        PriceAlertTrigger t = new PriceAlertTrigger();
        t.setTriggerId(id);
        t.setAlertId(3L);
        t.setSymbol("AAPL");
        t.setDirection(AlertDirection.ABOVE);
        t.setThreshold(new BigDecimal("190"));
        t.setMode(AlertMode.ONCE);
        t.setPrice(new BigDecimal("191"));
        t.setQuoteAt(Instant.parse(at));
        t.setTriggeredAt(Instant.parse(at));
        t.setReason("AAPL 191 >= 190 (ABOVE, ONCE)");
        t.setDeliveryStatus(DeliveryStatus.DELIVERED);
        t.setChannels(channels);
        t.setInApp(true);
        return t;
    }
}
