package com.tradingplatform.watchlist.alerts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tradingplatform.notifications.channel.ChannelSender;
import com.tradingplatform.notifications.channel.NotificationDispatcher;
import com.tradingplatform.notifications.channel.NotificationMessage;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.PreferenceResolver;
import com.tradingplatform.preferences.ResolvedAlertChannels;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;
import com.tradingplatform.watchlist.alerts.notify.InAppAlertNotifier;
import com.tradingplatform.watchlist.alerts.notify.PreferenceAwareAlertNotifier;
import com.tradingplatform.watchlist.alerts.notify.PriceAlertNotification;

/**
 * Unit tests for PreferenceAwareAlertNotifier: fired price alerts follow the customer's
 * alert preferences (EMAIL / SMS stubs, IN_APP bell).
 */
@DisplayName("PreferenceAwareAlertNotifier Tests")
class PreferenceAwareAlertNotifierTest {

    private static final long ACCOUNT = 7L;

    private FakePriceAlertStore store;
    private PreferenceResolver resolver;
    private ChannelSender email;
    private ChannelSender sms;
    private PreferenceAwareAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        store = new FakePriceAlertStore();
        resolver = mock(PreferenceResolver.class);
        when(resolver.resolveAlertChannels(ACCOUNT)).thenReturn(ResolvedAlertChannels.platformDefault(ACCOUNT));
        email = mock(ChannelSender.class);
        when(email.channel()).thenReturn(AlertChannel.EMAIL);
        sms = mock(ChannelSender.class);
        when(sms.channel()).thenReturn(AlertChannel.SMS);
        notifier = new PreferenceAwareAlertNotifier(new InAppAlertNotifier(store),
            new NotificationDispatcher(resolver, List.of(email, sms)), store);
    }

    @Test
    @DisplayName("Should email and text a fired alert when the customer chose those channels, and keep it out of the bell")
    void testEmailAndSms() {
        // Arrange: EMAIL and SMS only
        when(resolver.resolveAlertChannels(ACCOUNT)).thenReturn(new ResolvedAlertChannels(ACCOUNT, List.of(
            new AlertTarget(AlertChannel.EMAIL, "rahul@example.com"),
            new AlertTarget(AlertChannel.SMS, "+447700900123")), ResolvedAlertChannels.Source.CUSTOMER, false));
        PriceAlertNotification fired = fire(AlertMode.ONCE);

        // Act
        notifier.notify(fired);

        // Assert
        assertEquals("EMAIL,SMS", store.channelsByTrigger.get(fired.triggerId()));
        assertFalse(store.inAppByTrigger.get(fired.triggerId()));
        assertEquals(DeliveryStatus.DELIVERED, store.trigger(fired.triggerId()).getDeliveryStatus());

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(email).send(eq("rahul@example.com"), message.capture());
        verify(sms).send(eq("+447700900123"), any());
        assertEquals("Price alert: AAPL", message.getValue().subject());
        assertEquals("AAPL is at 190.10, above your 190.00 level. This alert is now off.", message.getValue().body());
    }

    @Test
    @DisplayName("Should notify in-app only, with no email or SMS, under the default preferences")
    void testDefaultInAppOnly() {
        PriceAlertNotification fired = fire(AlertMode.EVERY_QUOTE);

        notifier.notify(fired);

        assertEquals("IN_APP", store.channelsByTrigger.get(fired.triggerId()));
        assertTrue(store.inAppByTrigger.get(fired.triggerId()));
        assertEquals(DeliveryStatus.DELIVERED, store.trigger(fired.triggerId()).getDeliveryStatus());
        verify(email, never()).send(anyString(), any());
        verify(sms, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("Should send nothing when the trigger can't be marked delivered, so a retry never sends twice")
    void testNoSendWhenDeliveryFails() {
        when(resolver.resolveAlertChannels(ACCOUNT)).thenReturn(new ResolvedAlertChannels(ACCOUNT, List.of(
            new AlertTarget(AlertChannel.EMAIL, "rahul@example.com")), ResolvedAlertChannels.Source.CUSTOMER, false));
        PriceAlertNotification gone = new PriceAlertNotification(999L, 1L, ACCOUNT, "AAPL", AlertDirection.ABOVE,
            new BigDecimal("190.00"), AlertMode.ONCE, new BigDecimal("190.10"), Instant.now(), "crossed");

        assertThrows(IllegalStateException.class, () -> notifier.notify(gone));
        verify(email, never()).send(anyString(), any());
    }

    /** Seeds an alert and a PENDING trigger, as the evaluator does when an alert fires */
    private PriceAlertNotification fire(AlertMode mode) {
        PriceAlert alert = store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190.00", mode);
        PriceAlertTrigger trigger = new PriceAlertTrigger(alert, new BigDecimal("190.10"), Instant.now(), "AAPL crossed 190.00");
        store.upsertPending(trigger);
        return PriceAlertNotification.from(store.trigger(trigger.getTriggerId()));
    }
}
