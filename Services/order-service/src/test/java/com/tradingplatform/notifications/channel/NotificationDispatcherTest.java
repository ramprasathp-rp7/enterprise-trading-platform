package com.tradingplatform.notifications.channel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradingplatform.notifications.model.OrderNotification;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.PreferenceResolver;
import com.tradingplatform.preferences.PreferencesUnavailableException;
import com.tradingplatform.preferences.ResolvedAlertChannels;

/**
 * Unit tests for NotificationDispatcher and the email / SMS stubs.
 *
 * Tests cover:
 * - Preference resolution, with the in-app default when preferences are unavailable
 * - Channel bookkeeping on the stored notification
 * - Sending on every external channel, a failing channel not stopping the others
 * - Message wording, and masking of contact details in the stub logs
 */
@DisplayName("NotificationDispatcher Tests")
class NotificationDispatcherTest {

    private PreferenceResolver resolver;
    private ChannelSender email;
    private ChannelSender sms;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        resolver = mock(PreferenceResolver.class);
        email = mock(ChannelSender.class);
        when(email.channel()).thenReturn(AlertChannel.EMAIL);
        sms = mock(ChannelSender.class);
        when(sms.channel()).thenReturn(AlertChannel.SMS);
        dispatcher = new NotificationDispatcher(resolver, List.of(email, sms));
    }

    @Test
    @DisplayName("Should fall back to in-app when preferences cannot be read")
    void testFallbackWhenPreferencesUnavailable() {
        when(resolver.resolveAlertChannels(7L)).thenThrow(new PreferencesUnavailableException(7L, new RuntimeException("db")));

        ResolvedAlertChannels channels = dispatcher.resolveChannels(7L);

        assertEquals(List.of(AlertChannel.IN_APP), channels.channels());
        assertEquals(ResolvedAlertChannels.Source.PLATFORM_DEFAULT, channels.source());
    }

    @Test
    @DisplayName("Should record the channels and whether the bell shows it")
    void testApplyChannels() {
        OrderNotification n = notification("FILLED");
        dispatcher.applyChannels(n, customer(new AlertTarget(AlertChannel.SMS, "+447700900123"), AlertTarget.inApp()));

        assertEquals("SMS,IN_APP", n.getChannels());
        assertTrue(n.isInApp());
    }

    @Test
    @DisplayName("Should send on every external channel and keep going when one fails")
    void testSendExternal() {
        doThrow(new RuntimeException("smtp down")).when(email).send(any(), any());
        OrderNotification n = notification("REJECTED");

        assertDoesNotThrow(() -> dispatcher.sendExternal(n, customer(
            new AlertTarget(AlertChannel.EMAIL, "rahul@example.com"),
            new AlertTarget(AlertChannel.SMS, "+447700900123"),
            AlertTarget.inApp())));

        verify(email).send(eq("rahul@example.com"), any());
        verify(sms).send(eq("+447700900123"), any());
    }

    @Test
    @DisplayName("Should word filled, rejected and cancelled orders like the UI bell")
    void testMessages() {
        NotificationMessage filled = NotificationDispatcher.messageFor(notification("FILLED"));
        assertEquals("Order completed", filled.subject());
        assertEquals("Good news! You sold 30 RELIANCE.NS at 1,221.40.", filled.body());

        NotificationMessage rejected = NotificationDispatcher.messageFor(notification("REJECTED"));
        assertEquals("Order couldn't be completed", rejected.subject());
        assertEquals("Your order to sell 30 RELIANCE.NS at 1,215.70 was rejected. Reason: Insufficient funds.", rejected.body());

        NotificationMessage cancelled = NotificationDispatcher.messageFor(notification("CANCELLED"));
        assertEquals("Order cancelled", cancelled.subject());
        assertEquals("Your order to sell 30 RELIANCE.NS at 1,215.70 has been cancelled.", cancelled.body());

        OrderNotification bare = notification("FILLED");
        bare.setSymbol(null);
        bare.setQuantity(null);
        assertEquals("Good news! Your order was filled at 1,221.40.", NotificationDispatcher.messageFor(bare).body());
    }

    @Test
    @DisplayName("Should mask email addresses and phone numbers in the stub logs")
    void testMasking() {
        assertEquals("ra***@example.com", EmailSenderStub.mask("rahul@example.com"));
        assertEquals("a***@x.io", EmailSenderStub.mask("a@x.io"));
        assertEquals("***", EmailSenderStub.mask("not-an-email"));
        assertEquals("+44*******123", SmsSenderStub.mask("+447700900123"));
        assertEquals("***", SmsSenderStub.mask("+4412"));

        // The stubs only log: nothing to deliver, nothing thrown
        NotificationMessage message = new NotificationMessage(7L, "o1", "Order completed", "Good news!");
        assertDoesNotThrow(() -> new EmailSenderStub().send("rahul@example.com", message));
        assertDoesNotThrow(() -> new SmsSenderStub().send("+447700900123", message));
        assertEquals(AlertChannel.EMAIL, new EmailSenderStub().channel());
        assertEquals(AlertChannel.SMS, new SmsSenderStub().channel());
    }

    private static ResolvedAlertChannels customer(AlertTarget... targets) {
        return new ResolvedAlertChannels(7L, List.of(targets), ResolvedAlertChannels.Source.CUSTOMER, false);
    }

    private static OrderNotification notification(String status) {
        OrderNotification n = new OrderNotification();
        n.setAccountId(7L);
        n.setOrderId("6f1c2c1e-6a49-4d3a-9b1e-2c7f0d9f1a10");
        n.setStatus(status);
        n.setSymbol("RELIANCE.NS");
        n.setSide("SELL");
        n.setQuantity(30);
        n.setLimitPrice(new BigDecimal("1215.70"));
        n.setExecutedPrice("FILLED".equals(status) ? new BigDecimal("1221.40") : null);
        n.setReason("REJECTED".equals(status) ? "Insufficient funds" : null);
        return n;
    }
}
